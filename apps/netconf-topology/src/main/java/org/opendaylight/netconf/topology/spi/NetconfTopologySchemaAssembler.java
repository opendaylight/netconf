/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import com.google.common.util.concurrent.ForwardingBlockingQueue;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@NonNullByDefault
@Component(service = NetconfTopologySchemaAssembler.class, configurationPid = "org.opendaylight.netconf.topology")
@Designate(ocd = NetconfTopologySchemaAssembler.Configuration.class)
public final class NetconfTopologySchemaAssembler implements AutoCloseable {
    @ObjectClassDefinition
    public @interface Configuration {
        @AttributeDefinition(min = "1", description = """
            Maximum number of concurrent schema assmbly threads. This effectively limits the memory footprint by not
            allowing too many YANG parser executions to run at the same time.""")
        int assembler$_$max$_$threads() default 4;
    }

    private static final class SynchronousBlockingQueue extends ForwardingBlockingQueue<Runnable> {
        private final LinkedBlockingQueue<Runnable> delegate = new LinkedBlockingQueue<>();

        @Override
        protected BlockingQueue<Runnable> delegate() {
            return delegate;
        }

        @Override
        @SuppressWarnings("checkstyle:parameterName")
        public boolean offer(final Runnable o) {
            // ThreadPoolExecutor will spawn a new thread after core size is reached only if an offer is rejected. We
            // always do that and recover via the execution handler.
            return false;
        }
    }

    private static final ThreadFactory THREAD_FACTORY = new ThreadFactoryBuilder()
        .setNameFormat("topology-schema-assembler-%d")
        .setDaemon(true)
        .build();

    private final ThreadPoolExecutor executor;

    public NetconfTopologySchemaAssembler(final int maxThreads) {
        executor = new ThreadPoolExecutor(0, maxThreads, 0, TimeUnit.NANOSECONDS, new SynchronousBlockingQueue(),
            THREAD_FACTORY, (runnable, exec) -> {
                // if maximum number of threads are reached, the threadpool would reject the execution. We override that
                // behaviour and block until the queue accepts the task or we get interrupted
                try {
                    exec.getQueue().put(runnable);
                } catch (InterruptedException e) {
                    throw new RejectedExecutionException("Interrupted while waiting on the queue", e);
                }
            });
    }

    @Activate
    public NetconfTopologySchemaAssembler(final Configuration config) {
        this(config.assembler$_$max$_$threads());
    }

    @Override
    @Deactivate
    public void close() {
        executor.shutdown();
    }

    Executor executor() {
        return executor;
    }
}
