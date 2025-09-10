/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.InputStream;
import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.PendingRequest;
import org.opendaylight.netconf.transport.http.PendingRequestListener;
import org.opendaylight.netconf.transport.http.Response;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.HttpStatusCode;
import org.opendaylight.restconf.server.api.ConfigurationMetadata;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.impl.EndpointInvariants;

/**
 * An abstract implementation of {@link PendingRequest} contract for RESTCONF endpoint. This class and its subclasses
 * act as an intermediary between the Netty pipeline and backend {@link RestconfServer}:
 * <ul>
 *   <li>when instructed to start execution, this class allocates the {@link NettyServerRequest} and passes it to the
 *       concrete subclass, which is then responsible for passing the request, and whatever additional arguments, to the
 *       correct {@link RestconfServer} method</li>
 *   <li>upon {@link NettyServerRequest} success, this class calls down to the concrete subclass to translate the server
 *       response to the appropriate HTTP {@link Response} and then notifies the {@link PendingRequestListener}</li>
 *   <li>when {@link NettyServerRequest} fails, this class will wrap the failure in a {@link FormattableDataResponse},
 *       and passes it to {@link PendingRequestListener}</li>
 * </ul>
 *
 * @param <T> server response type
 */
// Note: not @NonNullByDefault because SpotBugs throws a tantrum on @Nullable field
abstract sealed class AbstractPendingRequest<T> extends PendingRequest<T>
        permits PendingRequestWithBody, PendingRequestWithoutBody {
    static final @NonNull DefaultHttpHeadersFactory HEADERS_FACTORY = DefaultHttpHeadersFactory.headersFactory();

    final @NonNull EndpointInvariants invariants;
    final @NonNull TransportSession session;
    final @NonNull URI targetUri;
    final @Nullable Principal principal;

    @NonNullByDefault
    AbstractPendingRequest(final EndpointInvariants invariants, final TransportSession session, final URI targetUri,
            final @Nullable Principal principal) {
        this.invariants = requireNonNull(invariants);
        this.session = requireNonNull(session);
        this.targetUri = requireNonNull(targetUri);
        this.principal = principal;
    }

    final @NonNull RestconfServer server() {
        return invariants.server();
    }

    /**
     * {@return the {@link MessageEncoding} to use with errors}
     */
    abstract @NonNull MessageEncoding errorEncoding();

    /**
     * {@return the {@link MessageEncoding} to use as RFC8639's {code RPC encoding}}
     */
    abstract @NonNull MessageEncoding requestEncoding();

    /**
     * Return the absolute URI pointing at the root API resource, as seen from the perspective of specified request.
     *
     * @return An absolute URI
     */
    final @NonNull URI restconfURI() {
        return targetUri.resolve(invariants.restconfPath());
    }

    @Override
    @SuppressWarnings("checkstyle:illegalCatch")
    public final void execute(final PendingRequestListener listener, final InputStream body) {
        try {
            execute(new NettyServerRequest<>(this, listener), body);
        } catch (RuntimeException e) {
            listener.requestFailed(this, e);
        }
    }

    /**
     * Execute this request on the backend {@link RestconfServer}.
     *
     * @param request the {@link NettyServerRequest} to pass to the server method
     * @param body request body, or {@code null} if not present or empty
     */
    @NonNullByDefault
    abstract void execute(NettyServerRequest<T> request, @Nullable InputStream body);

    @NonNullByDefault
    final void onFailure(final PendingRequestListener listener, final NettyServerRequest<T> request,
            final HttpStatusCode status, final FormattableBody body) {
        listener.requestComplete(this, new FormattableDataResponse(HttpResponseStatus.valueOf(status.code()), body,
            errorEncoding(), request.prettyPrint()));
    }

    @NonNullByDefault
    final void onSuccess(final PendingRequestListener listener, final NettyServerRequest<T> request, final T result) {
        listener.requestComplete(this, transformResult(request, result));
    }

    /**
     * Transform a RestconfServer result to a {@link Response}.
     *
     * @param request {@link NettyServerRequest} handle
     * @param result the result
     * @return A {@link Response}
     */
    @NonNullByDefault
    abstract Response transformResult(NettyServerRequest<?> request, T result);

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("target", targetUri);
    }

    @NonNullByDefault
    static final List<CharSequence> metadataHeaders(final ConfigurationMetadata metadata) {
        final var headers = new ArrayList<CharSequence>();

        final var etag = metadata.entityTag();
        if (etag != null) {
            headers.add(HttpHeaderNames.ETAG);
            headers.add(etag.value());
        }

        final var lastModified = metadata.lastModified();
        if (lastModified != null) {
            headers.add(HttpHeaderNames.LAST_MODIFIED);
            // FIXME: uses a thread local: we should be able to do better!
            headers.add(DateFormatter.format(Date.from(lastModified)));
        }

        return headers;
    }
}
