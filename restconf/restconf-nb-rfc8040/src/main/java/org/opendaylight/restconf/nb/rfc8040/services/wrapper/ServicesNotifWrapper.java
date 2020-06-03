/*
 * Copyright (c) 2018 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.services.wrapper;

import javax.ws.rs.Path;
import javax.ws.rs.core.UriInfo;
import org.glassfish.jersey.media.sse.EventOutput;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfDataStreamService;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.TransactionServicesNotifWrapper;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RestconfDataStreamServiceImpl;
import org.opendaylight.restconf.nb.rfc8040.streams.sse.SSEInitializer;

@Path("/")
public final class ServicesNotifWrapper implements TransactionServicesNotifWrapper {

    private final RestconfDataStreamService delegRestStream;

    private ServicesNotifWrapper(RestconfDataStreamService delegRestStream) {
        this.delegRestStream = delegRestStream;
    }

    public static ServicesNotifWrapper newInstance(SSEInitializer configuration) {
        RestconfDataStreamService delegRestStream = new RestconfDataStreamServiceImpl(configuration);
        return new ServicesNotifWrapper(delegRestStream);
    }

    @Override
    public EventOutput getSSE(String identifier, UriInfo uriInfo) {
        return this.delegRestStream.getSSE(identifier, uriInfo);
    }
}
