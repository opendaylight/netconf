/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.util;

import java.lang.annotation.Annotation;
import java.net.InetSocketAddress;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true, configurationPid = "netconf")
@Designate(ocd = NetconfConfigurationImpl.Configuration.class)
public class NetconfConfigurationImpl implements NetconfConfiguration {
    @ObjectClassDefinition
    public @interface Configuration {
        @AttributeDefinition(name = "tcp-address")
        String tcpAddress() default "127.0.0.1";
        @AttributeDefinition(name = "tcp-port", min = "0", max = "65535")
        int tcpPort() default 8383;
        @AttributeDefinition(name = "ssh-address")
        String sshAddress() default "0.0.0.0";
        @AttributeDefinition(name = "ssh-port", min = "0", max = "65535")
        int sshPort() default 1830;
        @AttributeDefinition(name = "sshpk-path")
        String sshPrivateKeyPath() default "./configuration/RSA.pk";
    }

    private static final Logger LOG = LoggerFactory.getLogger(NetconfConfigurationImpl.class);

    private NetconfConfigurationHolder netconfConfiguration;

    public NetconfConfigurationImpl() {
        // for DI
    }

    public NetconfConfigurationImpl(final String tcpServerAddress, final int tcpServerPort,
                                    final String sshServerAddress, final int sshServerPort,
                                    final String privateKeyPath) {
        activate(new Configuration() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Configuration.class;
            }

            @Override
            public String tcpAddress() {
                return tcpServerAddress;
            }

            @Override
            public int tcpPort() {
                return tcpServerPort;
            }

            @Override
            public String sshAddress() {
                return sshServerAddress;
            }

            @Override
            public int sshPort() {
                return sshServerPort;
            }

            @Override
            public String sshPrivateKeyPath() {
                return privateKeyPath;
            }
        });
    }

    @Activate
    void activate(final Configuration config) {
        final InetSocketAddress sshServerAddress = new InetSocketAddress(config.sshAddress(), config.sshPort());
        final InetSocketAddress tcpServerAddress = new InetSocketAddress(config.tcpAddress(), config.tcpPort());

        netconfConfiguration = new NetconfConfigurationHolder(tcpServerAddress, sshServerAddress,
            config.sshPrivateKeyPath());
        LOG.debug("CSS netconf server configuration was updated");
    }

    @Deactivate
    void deactivate(final Configuration config) {
        netconfConfiguration = null;
    }

    @Override
    public InetSocketAddress getSshServerAddress() {
        return netconfConfiguration.getSshServerAddress();
    }

    @Override
    public InetSocketAddress getTcpServerAddress() {
        return netconfConfiguration.getTcpServerAddress();
    }

    @Override
    public String getPrivateKeyPath() {
        return netconfConfiguration.getPrivateKeyPath();
    }
}
