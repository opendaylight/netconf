/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.databind.Request;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeJson$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeXml$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Encoding;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.QName;

/**
 * A {@link Request} to {@link RestconfServer}. It contains state and binding established by whoever is performing the
 * request on the transport (typically HTTP) layer. This includes:
 * <ul>
 *   <li>HTTP request {@link #queryParameters() query parameters},</li>
 *   <li>the transport {@link #session()} invoking this request</li>
 * </ul>
 *
 * <p>It notably does <b>not</b> hold the HTTP request path, nor the request body. Those are passed as separate
 * arguments to server methods as implementations of those methods are expected to act on them on multiple layers, i.e.
 * they are not a request invariant at the various processing layers.
 *
 * @param <R> type of reported result
 */
@NonNullByDefault
public sealed interface ServerRequest<R> extends Request<R> permits AbstractServerRequest, TransformedServerRequest {
    /**
     * Returns the {@link TransportSession} on which this request is executing, or {@code null} if there is no control
     * over transport sessions.
     *
     * @return the {@link TransportSession} on which this request is executing
     */
    @Nullable TransportSession session();

    /**
     * Returns the request's {@link QueryParameters}
     * .
     * @return the request's {@link QueryParameters}
     */
    QueryParameters queryParameters();

    /**
     * Returns the on-wire encoding of the original HTTP request body as a YANG {@link QName}, as related to the
     * purposes of establishing <a href="https://www.rfc-editor.org/rfc/rfc8639#section-2.4.2">the request encoding</a>.
     *
     * <p>Valid values are all identities derived from {@link Encoding} and users need to be cognisant of at least
     * {@link EncodeJson$I} and {@link EncodeXml$I}.
     *
     * <p>A typical implementation would consult mirror either HTTP request's {@code Content-type} header, or HTTP
     * requests' {@code Accept} header or determine the value in some other way.
     *
     * @return the encoding {@link QName} for the request body, or {@code null} if not applicable
     */
    QName requestEncoding();

    /**
     * Fail this request with specified {@link YangErrorsBody}.
     *
     * @param errors the {@link YangErrorsBody}
     */
    void failWith(YangErrorsBody errors);

    /**
     * Fail this request with specified {@link ErrorTag} and a {@link FormattableBody}.
     *
     * @param errorTag the {@link ErrorTag}
     * @param body the {@link FormattableBody}
     */
    void failWith(ErrorTag errorTag, FormattableBody body);

    @Override
    default <I> ServerRequest<I> transform(final Function<I, R> function) {
        return new TransformedServerRequest<>(this, function);
    }
}
