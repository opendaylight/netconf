/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.config;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.osgi.service.component.ComponentFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

/**
 * Default implementation of {@link GlobalNetconfConfiguration}. The idea here is that we pull in {@link Configuration}
 * as a service -- hence allowing services to dynamically react to changes. This means that, for example,
 * {@link Configuration#namePrefix()} controls the lifecycle of {@link GlobalNetconfThreadFactory}'s created services,
 * which in turn dominate {@link GlobalNetconfProcessingExecutor} and {@link GlobalNetconfSshScheduledExecutor}.
 *
 * <p>
 * Yes, this is ugly as hell, but completely compatible with what Blueprint was doing. In a perfect world we would want
 * to disconnect the thread factory name into the two thread pools, so that they can be configured separately.
 */
@Component(configurationPid = "org.opendaylight.netconf.config")
@Designate(ocd = Configuration.class)
@NonNullByDefault
public final class DefaultGlobalNetconfConfiguration implements GlobalNetconfConfiguration {
    private final Configuration configuration;

    @Reference(target = "(component.factory=" + GlobalNetconfThreadFactoryFactory.FACTORY_NAME + ")")
    ComponentFactory<GlobalNetconfThreadFactoryFactory> contextFactory = null;

    @Activate
    public DefaultGlobalNetconfConfiguration(final Configuration configuration) {
        this.configuration = requireNonNull(configuration);
    }

    @Override
    public Configuration configuration() {
        return configuration;
    }
}
