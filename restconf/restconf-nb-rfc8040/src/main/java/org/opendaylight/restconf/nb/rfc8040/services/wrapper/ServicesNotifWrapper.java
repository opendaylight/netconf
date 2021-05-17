/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.services.wrapper;

import javax.ws.rs.Path;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfDataStreamService;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RestconfDataStreamServiceImpl;
import org.opendaylight.restconf.nb.rfc8040.streams.sse.SSEInitializer;

/**
 * Wrapper for service.
 * <ul>
 * <li>{@link RestconfDataStreamService}
 * </ul>
 */
@Path("/")
public final class ServicesNotifWrapper implements RestconfDataStreamService {

    private final RestconfDataStreamService delegRestStream;

    private ServicesNotifWrapper(final RestconfDataStreamService delegRestStream) {
        this.delegRestStream = delegRestStream;
    }

    public static ServicesNotifWrapper newInstance(final SSEInitializer configuration) {
        RestconfDataStreamService delegRestStream = new RestconfDataStreamServiceImpl(configuration);
        return new ServicesNotifWrapper(delegRestStream);
    }

    @Override
    public void getSSE(final String identifier, final UriInfo uriInfo, final SseEventSink sink, final Sse sse) {
        this.delegRestStream.getSSE(identifier, uriInfo, sink, sse);
    }
}
