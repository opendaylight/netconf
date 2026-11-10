/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static com.google.common.base.Verify.verifyNotNull;
import static io.netty.handler.codec.http.HttpHeaderValues.CHUNKED;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A permanent HTTP/1.1 request dispatcher, sitting between {@link HttpServerCodec} and {@link HttpObjectAggregator}. It
 * examines every initial {@link HttpRequest} and selects the {@link RequestMode} in which that request, and only that
 * request, is going to be processed. The selected mode stays in effect until the request's {@link LastHttpContent} has
 * been forwarded, at which point the dispatcher resets and evaluates the next request arriving on the same connection.
 *
 * <p>Only a successfully-decoded request using {@code Transfer-Encoding: chunked} is
 * selected as {@link RequestMode#STREAMING} and routed past {@link HttpObjectAggregator}. Its headers reach the
 * session immediately, and subsequent content is delivered to the associated input stream. The body length is not
 * known from the initial headers: a chunked request is streamed even if it eventually carries no body. A failed
 * request or one declaring {@code Content-Length}, including one exceeding the configured limit, is selected as
 * {@link RequestMode#AGGREGATED} and forwarded through the aggregator onto the existing {@link FullHttpRequest} path.
 *
 * <p>Streamed headers skip only the aggregator. Subsequent content is delivered to the request body stream,
 * preserving downstream authentication and session preparation. Each streamed content buffer is checked against
 * the same body limit as the aggregator before being appended. Overflow or malformed content terminates the
 * connection and fails the body, releasing queued bytes and waking its reader. Overflow receives 413. Bad framing
 * or malformed chunks receive 400. Unsupported transfer codings receive 501. If a response has already started,
 * rejection closes the connection without sending a second response. Closing the body alone does not indicate
 * that a response has been sent.
 *
 * <p>Framing is checked for every successfully decoded request, including transfer codings which would otherwise
 * select aggregation. HTTP/1.0 transfer encoding is rejected as invalid framing.
 *
 * <p>Downstream HTTP/1.1 sessions must emit responses in request order. The dispatcher counts final response
 * boundaries and waits for earlier responses to flush before sending a transport rejection. Later response writes
 * are released immediately, but their promises fail only after the transport response finishes or the connection
 * closes, so session failure listeners cannot prematurely discard a backpressured error response.
 *
 * <p>A rejection queued behind a response which never finishes (for example an SSE stream) cannot be sent without
 * violating HTTP/1.1 response ordering. It remains pending until that response finishes or the connection closes.
 * This dispatcher imposes no timeout. Incoming content continues to be discarded while the rejection is pending.
 */
@NonNullByDefault
final class Http1RequestDispatcher extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(Http1RequestDispatcher.class);
    /**
     * The name under which {@link HttpObjectAggregator} is installed in the pipeline.
     *
     * <p>Looking the aggregator up by name is deliberate: {@code ChannelPipeline.context(Class)} matches assignable
     * types scanning from the head, and {@link HttpServerUpgradeHandler}, which the cleartext h2c pipeline places
     * ahead of this handler, extends {@link HttpObjectAggregator}.
     */
    private static final String AGGREGATOR_NAME = "http1-aggregator";

    /**
     * The name under which this handler is installed in the pipeline.
     */
    @VisibleForTesting
    static final String HANDLER_NAME = "http1-dispatcher";

    private final int maxRequestBodySize;

    /**
     * The way the request currently being received is being processed.
     */
    private enum RequestMode {
        /**
         * The request is being forwarded through {@link HttpObjectAggregator}, ending up as a {@link FullHttpRequest}.
         */
        AGGREGATED,
        /**
         * The request is eligible for being forwarded past {@link HttpObjectAggregator}, ending up as an
         * {@link StreamingHttpRequest} whose body receives subsequent content.
         */
        STREAMING
    }

    /**
     * Terminal connection state. A non-null status is waiting for earlier responses. A null status means that
     * the transport response has been submitted or the connection is closing without another response.
     */
    private record Rejection(long afterResponses, @Nullable HttpResponseStatus status, ChannelPromise completion) {
        Rejection withoutResponse() {
            return new Rejection(afterResponses, null, completion);
        }

        void discard(final Object msg, final ChannelPromise promise) {
            ReferenceCountUtil.release(msg);
            // Failure listeners may close the channel: wait until the transport response finishes.
            completion.addListener(ignored -> promise.tryFailure(new ClosedChannelException()));
        }
    }

    // The aggregator's own context, i.e. where the messages of a streamed request are re-injected. Established by
    // handlerAdded(), as neither the aggregator nor its context changes while this handler is in the pipeline.
    @SuppressFBWarnings(value = "NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR",
        justification = "Field is @Nullable and is initialized by handlerAdded(), not by the constructor.")
    private @Nullable ChannelHandlerContext aggregatorContext;
    @SuppressFBWarnings(value = "NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR",
        justification = "Field is @Nullable and is initialized only when rejecting a request.")
    private @Nullable Rejection rejection;
    // Per-request state, reset when the next request head arrives.
    private long receivedBodyBytes;
    // Request and final-response sequence numbers. Informational responses do not advance these counters.
    private long requestsReceived;
    private long responsesStarted;
    private long responsesEnded;
    private long responsesFlushed;
    private boolean finalResponseInProgress;
    // True from the request head until its LastHttpContent.
    private boolean inRequest;
    // Mode of that request, meaningful only while inRequest is true.
    private RequestMode mode = RequestMode.AGGREGATED;

    @SuppressFBWarnings(value = { "NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR", "NP_STORE_INTO_NONNULL_FIELD" },
        justification = "Field is @Nullable and is present only while receiving a streamed request body.")
    private @Nullable RequestBodyInputStream body;

    Http1RequestDispatcher(final int maxRequestBodySize) {
        this.maxRequestBodySize = maxRequestBodySize;
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        final var pipeline = ctx.pipeline();
        pipeline.addAfter(ctx.name(), AGGREGATOR_NAME, new HttpObjectAggregator(maxRequestBodySize));
        aggregatorContext = verifyNotNull(pipeline.context(AGGREGATOR_NAME));
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (rejection != null) {
            ReferenceCountUtil.release(msg);
            return;
        }

        // Someone upstream has already aggregated this request: there is nothing to dispatch and no body to follow
        if (msg instanceof FullHttpRequest) {
            ++requestsReceived;
            ctx.fireChannelRead(msg);
            return;
        }

        if (!inRequest) {
            if (msg instanceof HttpRequest request) {
                startRequest(ctx, request);
            } else {
                // The codec should never produce content outside of a request, just pass it on
                LOG.warn("{}: unexpected {} outside of a request", ctx.channel(), msg.getClass().getSimpleName());
                ctx.fireChannelRead(msg);
            }
            return;
        }

        if (msg instanceof HttpContent content) {
            if (mode == RequestMode.STREAMING) {
                try {
                    final var currentBody = verifyNotNull(body);
                    if (content.decoderResult().isFailure()) {
                        reject(ctx, HttpResponseStatus.BAD_REQUEST, content.decoderResult().cause());
                    } else if (content.content().readableBytes() > maxRequestBodySize - receivedBodyBytes) {
                        reject(ctx, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE,
                            new IOException("Request body exceeds " + maxRequestBodySize + " bytes"));
                    } else {
                        receivedBodyBytes += content.content().readableBytes();
                        // A closed consumer discards content, but these bytes still count toward the transport limit.
                        currentBody.append(content.content());
                        if (content instanceof LastHttpContent) {
                            currentBody.complete();
                            body = null;
                        }
                    }
                } catch (IOException e) {
                    // append() throws only when the stream already retains a failure or interruption.
                    LOG.debug("{}: request body no longer accepts content", ctx.channel(), e);
                    closeRejected(ctx);
                } finally {
                    ReferenceCountUtil.release(content);
                }
            } else {
                forward(ctx, content);
            }
            if (content instanceof LastHttpContent) {
                LOG.debug("{}: {} request complete, next request will be evaluated afresh", ctx.channel(), mode);
                inRequest = false;
            }
        } else {
            LOG.warn("{}: unexpected {} in {} mode", ctx.channel(), msg.getClass().getSimpleName(), mode);
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
        final var currentRejection = rejection;
        if (currentRejection != null && responsesEnded >= currentRejection.afterResponses) {
            currentRejection.discard(msg, promise);
            return;
        }
        if (msg instanceof HttpResponse response && response.status().code() >= 200) {
            ++responsesStarted;
            finalResponseInProgress = true;
        }
        if (finalResponseInProgress && msg instanceof LastHttpContent) {
            finalResponseInProgress = false;
            final var sequence = ++responsesEnded;
            final var observable = promise.unvoid();
            observable.addListener(future -> {
                if (future.isSuccess()) {
                    responsesFlushed = sequence;
                    sendRejection(ctx);
                } else if (rejection != null) {
                    closeRejected(ctx);
                }
            });
            ctx.write(msg, observable);
        } else {
            ctx.write(msg, promise);
        }
    }

    private void startRequest(final ChannelHandlerContext ctx, final HttpRequest request) {
        receivedBodyBytes = 0;
        ++requestsReceived;
        final var framingError = checkFraming(request);
        if (framingError != null) {
            reject(ctx, framingError, new IOException("Invalid or unsupported request framing"));
            return;
        }
        // note: an expectation other than 100-continue is left to HttpObjectAggregator, which answers 417 and discards
        //       the body, i.e. more than this handler should be reimplementing. HttpUtil.isUnsupportedExpectation() is
        //       not public, hence the explicit comparison.
        final var expect = request.headers().get(HttpHeaderNames.EXPECT);
        final var expectationSupported = expect == null || HttpHeaderValues.CONTINUE.contentEqualsIgnoreCase(expect);

        mode = request.decoderResult().isSuccess()
            && HttpUtil.isTransferEncodingChunked(request) && expectationSupported ? RequestMode.STREAMING
                : RequestMode.AGGREGATED;
        LOG.debug("{}: {} {} dispatched as {}", ctx.channel(), request.method(), request.uri(), mode);

        if (mode == RequestMode.STREAMING && HttpUtil.is100ContinueExpected(request)) {
            // HttpObjectAggregator answers this for the requests it aggregates, hence a request routed past it has to
            // have its expectation answered here.
            request.headers().remove(HttpHeaderNames.EXPECT);
            ctx.writeAndFlush(new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.CONTINUE))
                .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        }

        // FullHttpRequest is the only HttpRequest which is also a LastHttpContent, and those have been dealt
        // with in channelRead() already, so there is always a body to follow here
        inRequest = true;
        if (mode == RequestMode.STREAMING) {
            final var streaming = new StreamingHttpRequest(request);
            body = streaming.body();
            forward(ctx, streaming);
        } else {
            forward(ctx, request);
        }
    }

    private static @Nullable HttpResponseStatus checkFraming(final HttpRequest request) {
        if (!request.headers().contains(HttpHeaderNames.TRANSFER_ENCODING)) {
            return null;
        }
        // Reject HTTP/1.0 before checking decoderResult: even a decoder-rejected request requires closure.
        if (HttpVersion.HTTP_1_0.equals(request.protocolVersion())) {
            return HttpResponseStatus.BAD_REQUEST;
        }
        if (!request.decoderResult().isSuccess()) {
            // Preserve the decoder failure for the aggregated path and the session's error response.
            return null;
        }
        final var encodings = String.join(",", request.headers().getAll(HttpHeaderNames.TRANSFER_ENCODING))
            .split(",", -1);
        // Guard requests supplied by upstream handlers too. With RFC9112 validation, Netty already rejects
        // Content-Length combined with chunked encoding. Older decoder configurations may strip the length.
        if (request.headers().contains(HttpHeaderNames.CONTENT_LENGTH)
                || !CHUNKED.contentEqualsIgnoreCase(encodings[encodings.length - 1].trim())) {
            return HttpResponseStatus.BAD_REQUEST;
        }
        for (int i = 0; i < encodings.length - 1; ++i) {
            final var coding = encodings[i].trim();
            if (coding.isEmpty() || CHUNKED.contentEqualsIgnoreCase(coding)) {
                return HttpResponseStatus.BAD_REQUEST;
            }
        }
        return encodings.length == 1 ? null : HttpResponseStatus.NOT_IMPLEMENTED;
    }

    private void closeRejected(final ChannelHandlerContext ctx) {
        markClosing(ctx);
        cancelBody();
        ctx.close();
    }

    private void reject(final ChannelHandlerContext ctx, final HttpResponseStatus status, final Throwable cause) {
        final var responseStarted = responsesStarted >= requestsReceived;
        rejection = new Rejection(requestsReceived - 1, responseStarted ? null : status, ctx.newPromise());
        final var currentBody = body;
        body = null;
        if (responseStarted) {
            if (currentBody != null) {
                currentBody.cancel();
            }
            ctx.close();
            return;
        }
        sendRejection(ctx);
        if (currentBody != null) {
            currentBody.fail(cause);
        }
    }

    private void sendRejection(final ChannelHandlerContext ctx) {
        final var current = rejection;
        if (current == null || current.status == null || responsesFlushed < current.afterResponses) {
            return;
        }
        rejection = current.withoutResponse();
        final var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, current.status);
        HttpUtil.setContentLength(response, 0);
        HttpUtil.setKeepAlive(response, false);
        // Bypass our outbound rejection guard only for this terminal transport response.
        ctx.writeAndFlush(response, current.completion).addListener(ChannelFutureListener.CLOSE);
    }

    private void markClosing(final ChannelHandlerContext ctx) {
        final var current = rejection;
        rejection = new Rejection(0, null, current == null ? ctx.newPromise() : current.completion);
    }

    private void failRejection() {
        final var current = rejection;
        if (current != null) {
            rejection = current.withoutResponse();
            current.completion.tryFailure(new ClosedChannelException());
        }
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        failRejection();
        cancelBody();
        super.channelInactive(ctx);
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) throws Exception {
        failRejection();
        cancelBody();
        super.handlerRemoved(ctx);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
        markClosing(ctx);
        if (body != null) {
            body.fail(cause);
            body = null;
        }
        ctx.close();
        super.exceptionCaught(ctx, cause);
    }

    private void cancelBody() {
        if (body != null) {
            body.cancel();
            body = null;
        }
    }

    private void forward(final ChannelHandlerContext ctx, final Object msg) {
        if (mode == RequestMode.AGGREGATED) {
            ctx.fireChannelRead(msg);
        } else {
            // Firing from the aggregator's own context delivers the message to the handler following it, i.e. skips
            // aggregation without skipping anything else, most notably not the AuthHandler
            verifyNotNull(aggregatorContext).fireChannelRead(msg);
        }
    }
}
