/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.server.api.EventStreamGetParams;
import org.opendaylight.restconf.server.api.MonitoringEncoding;
import org.opendaylight.restconf.server.spi.RestconfStream;

/**
 * A factory for creating {@link SSESender}s.
 */
@NonNullByDefault
interface SSESenderFactory {

    void newSSESender(SseEventSink sink, Sse sse, RestconfStream<?> stream, MonitoringEncoding encoding,
        EventStreamGetParams getParams);
}
