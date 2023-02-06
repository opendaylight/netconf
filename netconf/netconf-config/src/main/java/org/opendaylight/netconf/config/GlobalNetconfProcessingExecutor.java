/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.config;

import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.config.threadpool.util.FlexibleThreadPoolWrapper;
import org.opendaylight.controller.config.threadpool.util.NamingThreadPoolFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

@Singleton
@Component(service = ThreadPool.class, property = "type=global-netconf-ssh-scheduled-executor")
public final class GlobalNetconfProcessingExecutor extends FlexibleThreadPoolWrapper {
    public static final int DEFAULT_MIN_THREAD_COUNT = 1;
    public static final int DEFAULT_MAX_THREAD_COUNT = 4;
    public static final long DEFAULT_KEEPALIVE_MILLIS = 600000;

    public GlobalNetconfProcessingExecutor(final String namePrefix, final int minThreadCount, final int maxThreadCount,
            final long keepAlive) {
        // FIXME: shared thread factory!
        super(minThreadCount, maxThreadCount, keepAlive, TimeUnit.MILLISECONDS,
            new NamingThreadPoolFactory(namePrefix));
    }

    @Inject
    public GlobalNetconfProcessingExecutor() {
        // FIXME: proper name!
        this("", DEFAULT_MIN_THREAD_COUNT, DEFAULT_MAX_THREAD_COUNT, DEFAULT_KEEPALIVE_MILLIS);
    }

    @Activate
    public GlobalNetconfProcessingExecutor(final Configuration configuration) {
        this(configuration.namePrefix(), configuration.minThreadCountFlexibleThreadPool(),
            configuration.maxThreadCountFlexibleThreadPool(), configuration.keepAliveMillisFlexibleThreadPool());
    }


    @Override
    @PreDestroy
    @Deactivate
    public void close() {
        super.close();
    }
}
