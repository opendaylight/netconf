/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.transport.api.SSHNegotiatedAlgListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;

/**
 * Basic interface for Netconf client factory.
 */
public interface NetconfClientFactory extends AutoCloseable {
    /**
     * Create a NETCONF client. Network communication has to be set up based on network protocol specified in
     * clientConfiguration
     *
     * @param clientConfiguration configuration
     * @return A future producing the {@link NetconfClientSession}
     * @throws UnsupportedConfigurationException if any transport configuration parameters is invalid
     */
    ListenableFuture<NetconfClientSession> createClient(NetconfClientConfiguration clientConfiguration,
        SSHNegotiatedAlgListener algListener) throws UnsupportedConfigurationException;
}
