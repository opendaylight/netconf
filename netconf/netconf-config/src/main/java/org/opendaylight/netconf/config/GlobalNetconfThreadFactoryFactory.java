/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.config;

import static com.google.common.base.Verify.verifyNotNull;

import java.util.Map;
import org.opendaylight.controller.config.threadpool.util.NamingThreadPoolFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is your ordinary
 * <a href="https://www.danstroot.com/posts/2018-10-03-hammer-factories">hammer factory factory<a>. The problem is we
 * <b>really</b> want to keep the same thread factory as long as {@link Configuration#namePrefix()} does not change.
 */
@Component(factory = GlobalNetconfThreadFactoryFactory.FACTORY_NAME, service = GlobalNetconfThreadFactory.class)
public final class GlobalNetconfThreadFactoryFactory implements GlobalNetconfThreadFactory {
    // OSGi DS Component Factory name
    static final String FACTORY_NAME = "org.opendaylight.netconf.config.GlobalNetconfThreadFactoryFactory";
    // The property holding NamingThreadPoolFactory.getNamePrefix()
    static final String NAME_PREFIX = "name-prefix";

    private static final Logger LOG = LoggerFactory.getLogger(GlobalNetconfThreadFactoryFactory.class);

    private String namePrefix;
    private NamingThreadPoolFactory delegate;

    @Activate
    void activate(final Map<String, ?> properties) {
        namePrefix = (String) verifyNotNull(properties.get(NAME_PREFIX));
        delegate = new NamingThreadPoolFactory(namePrefix);
        LOG.info("GlobalNetconfThreadFactoryFactory prefix {} activated", namePrefix);
    }

    @Deactivate
    void deactivate() {
        delegate = null;
        LOG.info("GlobalNetconfThreadFactoryFactory prefix {} deactivated", namePrefix);
    }

    @Override
    public Thread newThread(final Runnable r) {
        return delegate.newThread(r);
    }
}
