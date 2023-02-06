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
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.util.ScheduledThreadPoolWrapper;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

@Singleton
@Component(factory = GlobalNetconfSshScheduledExecutor.FACTORY_NAME, service = ScheduledThreadPool.class)
public final class GlobalNetconfSshScheduledExecutor extends ScheduledThreadPoolWrapper {
    public static final int DEFAULT_MAX_THREAD_COUNT = 8;

    // OSGi DS Component Factory name
    static final String FACTORY_NAME = "org.opendaylight.netconf.config.GlobalNetconfSshScheduledExecutor";

    private static final String PROP_MAX_THREAD_COUNT = ".maxThreadCount";
    private static final String PROP_THREAD_FACTORY = ".threadFactory";

    public GlobalNetconfSshScheduledExecutor(final GlobalNetconfThreadFactory threadFactory, final int maxThreadCount) {
        super(maxThreadCount, threadFactory);
    }

    @Inject
    public GlobalNetconfSshScheduledExecutor(final GlobalNetconfThreadFactory threadFactory) {
        this(threadFactory, DEFAULT_MAX_THREAD_COUNT);
    }

    @Activate
    public GlobalNetconfSshScheduledExecutor(final Map<String, ?> properties) {
        this(GlobalNetconfConfiguration.extractProp(properties, PROP_THREAD_FACTORY, GlobalNetconfThreadFactory.class),
            GlobalNetconfConfiguration.extractProp(properties, PROP_MAX_THREAD_COUNT, Integer.class));
    }

    @Override
    @PreDestroy
    @Deactivate
    public void close() {
        super.close();
    }

    static Map<String, ?> props(final GlobalNetconfThreadFactory threadFactory, final Configuration configuration) {
        return Map.of(
            "type", "type=global-netconf-ssh-scheduled-executor",
            PROP_MAX_THREAD_COUNT, configuration.maxThreadCountScheduledThreadPool(),
            PROP_THREAD_FACTORY, requireNonNull(threadFactory));
    }
}
