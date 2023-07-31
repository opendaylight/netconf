/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import io.netty.util.concurrent.Future;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;

/**
 * Basic interface for Netconf client factory.
 */
public interface NetconfClientFactory {

    /**
     * Create netconf client. Network communication has to be set up based on network protocol specified in
     * clientConfiguration
     *
     * @param clientConfiguration configuration
     * @return netconf client as {@link NetconfClientSession} future
     * @throws UnsupportedConfigurationException if any transport configuration parameters is invalid
     */
    Future<NetconfClientSession> createClient(NetconfClientConfiguration clientConfiguration)
        throws UnsupportedConfigurationException;

}
