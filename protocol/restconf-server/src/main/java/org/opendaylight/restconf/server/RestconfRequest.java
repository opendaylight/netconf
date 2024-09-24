/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import io.netty.handler.codec.http.FullHttpResponse;
import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
abstract class RestconfRequest {
    // FIXME: expand the semantics of this class:
    //        - it is instantiated from the EventLoop of a particular RestconfSession
    //        - it may represent an HTTP/1.1 (pipelined) or HTTP/2 (concurrent) request
    //        - it may be completed synchronously during dispatch (i.e. due to validation)
    //        - it may be tied to a ServerRequest, which usually completes asynchronously
    //          -- this transition has to be explicit, as RestconfSession needs to be able to perform some bookkeeping
    //             w.r.t. how subsequent HttpRequests are handled
    //          -- ServerRequests typically finish with a FormattableBody, which can contain a huge entity, which we
    //             do *not* want to completely buffer to a FullHttpResponse
    //          -- that means each asynchronously-completed request needs to result in a virtual thread which translates
    //             the result into either a FullHttpResponse (if under 256KiB) or a HttpResponse followed by a number
    //             of HttpContents and finished by a LastHttpContent

    abstract void onSuccess(FullHttpResponse response);
}
