/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.northbound;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.netconf.transport.ssh.SSHTransportStackFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple holder of {@link SSHTransportStackFactory}, bridging to configuration. The factory encapsulates Netty
 * event loop groups, which is suitable for use with TLS/TCP transports as well.
 */
@Singleton
@Component(service = TransportFactoryHolder.class, configurationPid = "org.opendaylight.netconf.northbound.netty")
@Designate(ocd = TransportFactoryHolder.Configuration.class)
public final class TransportFactoryHolder implements AutoCloseable {
    /**
     * Configuration of the NETCONF northbound.
     */
    @ObjectClassDefinition()
    public @interface Configuration {
        @AttributeDefinition(name = "Number of Netty boss threads", min = "0")
        int boss$_$threads() default 0;

        @AttributeDefinition(name = "Number of Netty worker threads", min = "0")
        int worker$_$threads() default 0;
    }

    private static final Logger LOG = LoggerFactory.getLogger(TransportFactoryHolder.class);

    private final SSHTransportStackFactory factory;

    @Inject
    public TransportFactoryHolder() {
        this(0, 0);
    }

    @Activate
    public TransportFactoryHolder(final Configuration configuration) {
        this(configuration.boss$_$threads(), configuration.worker$_$threads());
    }

    public TransportFactoryHolder(final int bossThreads, final int workerThreads) {
        factory = new SSHTransportStackFactory("odl-netconf-nb-worker", workerThreads,
            "odl-netconf-nb-boss", bossThreads);
        LOG.info("NETCONF Northbound Netty context initialized");
    }

    @PreDestroy
    @Deactivate
    @Override
    public void close() {
        LOG.info("NETCONF Northbound Netty context shutting down");
        factory.close();
        LOG.info("NETCONF Northbound Netty context shut down");
    }

    SSHTransportStackFactory factory() {
        return factory;
    }
}
