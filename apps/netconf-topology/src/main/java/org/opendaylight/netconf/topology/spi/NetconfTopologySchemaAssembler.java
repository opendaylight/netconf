/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.controller.config.threadpool.util.FlexibleThreadPoolWrapper;
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
        @AttributeDefinition(min = "0")
        int assembler$_$min$_$threads() default 1;
        @AttributeDefinition(min = "1")
        int assembler$_$max$_$threads() default 4;
        @AttributeDefinition(min = "0")
        long assembler$_$keep$_$alive$_$millis() default 60_000;
    }

    private static final ThreadFactory THREAD_FACTORY = new ThreadFactoryBuilder()
        .setNameFormat("topology-schema-assembler-%d")
        .setDaemon(true)
        .build();

    private final FlexibleThreadPoolWrapper threadPool;

    public NetconfTopologySchemaAssembler(final int minThreads, final int maxThreads, final long keepAliveTime,
            final TimeUnit unit) {
        threadPool = new FlexibleThreadPoolWrapper(minThreads, maxThreads, keepAliveTime, unit, THREAD_FACTORY);
    }

    @Activate
    public NetconfTopologySchemaAssembler(final Configuration config) {
        this(config.assembler$_$min$_$threads(), config.assembler$_$max$_$threads(),
            config.assembler$_$keep$_$alive$_$millis(), TimeUnit.MILLISECONDS);
    }

    @Override
    @Deactivate
    public void close() {
        threadPool.close();
    }

    Executor executor() {
        return threadPool.getExecutor();
    }
}
