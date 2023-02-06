/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.config;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.config.threadpool.util.FlexibleThreadPoolWrapper;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

@Singleton
@Component(factory = GlobalNetconfProcessingExecutor.FACTORY_NAME, service = ThreadPool.class)
public final class GlobalNetconfProcessingExecutor extends FlexibleThreadPoolWrapper {
    public static final String OSGI_TYPE = "global-netconf-processing-executor";
    public static final int DEFAULT_MIN_THREAD_COUNT = 1;
    public static final int DEFAULT_MAX_THREAD_COUNT = 4;
    public static final long DEFAULT_KEEPALIVE_MILLIS = 600000;

    // OSGi DS Component Factory name
    static final String FACTORY_NAME = "org.opendaylight.netconf.config.GlobalNetconfProcessingExecutor";

    private static final String PROP_KEEPALIVE = ".keepAlive";
    private static final String PROP_MIN_THREAD_COUNT = ".minThreadCount";
    private static final String PROP_MAX_THREAD_COUNT = ".maxThreadCount";
    private static final String PROP_THREAD_FACTORY = ".threadFactory";

    public GlobalNetconfProcessingExecutor(final GlobalNetconfThreadFactory threadFactory, final int minThreadCount,
            final int maxThreadCount, final long keepAliveMillis) {
        super(minThreadCount, maxThreadCount, keepAliveMillis, TimeUnit.MILLISECONDS, threadFactory);
    }

    @Inject
    public GlobalNetconfProcessingExecutor(final GlobalNetconfThreadFactory threadFactory) {
        this(threadFactory, DEFAULT_MIN_THREAD_COUNT, DEFAULT_MAX_THREAD_COUNT, DEFAULT_KEEPALIVE_MILLIS);
    }

    @Activate
    public GlobalNetconfProcessingExecutor(final Map<String, ?> properties) {
        this(GlobalNetconfConfiguration.extractProp(properties, PROP_THREAD_FACTORY, GlobalNetconfThreadFactory.class),
            GlobalNetconfConfiguration.extractProp(properties, PROP_MIN_THREAD_COUNT, Integer.class),
            GlobalNetconfConfiguration.extractProp(properties, PROP_MAX_THREAD_COUNT, Integer.class),
            GlobalNetconfConfiguration.extractProp(properties, PROP_KEEPALIVE, Long.class));
    }

    @Override
    @PreDestroy
    @Deactivate
    public void close() {
        super.close();
    }

    static Map<String, ?> props(final GlobalNetconfThreadFactory threadFactory, final Configuration configuration) {
        return Map.of(
            "type", OSGI_TYPE,
            PROP_THREAD_FACTORY, requireNonNull(threadFactory),
            PROP_KEEPALIVE, configuration.keep$_$alive$_$millis$_$flexible$_$thread$_$pool(),
            PROP_MIN_THREAD_COUNT, configuration.min$_$thread$_$count$_$flexible$_$thread$_$pool(),
            PROP_MAX_THREAD_COUNT, configuration.max$_$thread$_$count$_$flexible$_$thread$_$pool());
    }
}
