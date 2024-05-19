/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.client;

import com.google.common.util.concurrent.ListenableFuture;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddressNoZone;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.client.rev240208.restconf.client.app.grouping.initiate.RestconfServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.client.rev240208.restconf.client.app.grouping.initiate.restconf.server.endpoints.Endpoint;

/**
 * An implementation of RESTCONF client services. Allows establishing {@link RestconfConnection}s to remote RESTCONF
 * servers via {@link #connect(IpAddressNoZone, PortNumber)} and related methods.
 */
@NonNullByDefault
public interface RestconfClient {

    ListenableFuture<RestconfConnection> connect(RestconfServer server) throws UnsupportedConfigurationException;

    ListenableFuture<RestconfConnection> connect(String serverName, Endpoint endpoint)
        throws UnsupportedConfigurationException;

    // FIXME: listen(Listen configuration) as well
}
