/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.config;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.util.NamingThreadPoolFactory;
import org.opendaylight.controller.config.threadpool.util.ScheduledThreadPoolWrapper;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

@Singleton
@Component(service = ScheduledThreadPool.class, property = "type=global-netconf-ssh-scheduled-executor")
public final class GlobalNetconfSshScheduledExecutor extends ScheduledThreadPoolWrapper {
    public static final int DEFAULT_MAX_THREAD_COUNT = 8;

    @Inject
    public GlobalNetconfSshScheduledExecutor(final String namePrefix, final int maxThreadCount) {
        // FIXME: shared thread factory!
        super(maxThreadCount, new NamingThreadPoolFactory(namePrefix));
    }

    @Activate
    public GlobalNetconfSshScheduledExecutor(final Configuration configuration) {
        this(configuration.namePrefix(), configuration.maxThreadCountScheduledThreadPool());
    }

    @Override
    @PreDestroy
    @Deactivate
    public void close() {
        super.close();
    }
}
