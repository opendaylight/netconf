/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import com.google.common.annotations.Beta;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.io.IOException;
import java.io.OutputStream;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A response to request. It can be turned into a response via {@link #toHttpResponse(HttpVersion)}.
 */
@Beta
@NonNullByDefault
public interface Response {

    HttpResponseStatus status();

    void fillHeaders(HttpHeaders headers);

    void writeBody(OutputStream out) throws IOException;

//    /**
//     * Return a {@link FullHttpResponse} representation of this object.
//     *
//     * @param alloc {@link ByteBufAllocator} to use for ByteBuf allocation
//     * @param version HTTP version to use
//     * @return a {@link FullHttpResponse}
//     * @throws IOException when an I/O error occurs
//     */
//    FullHttpResponse toHttpResponse(ByteBufAllocator alloc, HttpVersion version);
//
//    ResponseProof writeHttpResponse(ResponseOutput out);
//
//
//    @FunctionalInterface
//    interface ResponseBodyWriter {
//
//        void writeBody(OutputStream out) throws IOException;
//    }
//
//    final class ResponseOutput {
//        private final ChannelHandlerContext ctx;
//
//        ResponseOutput(final ChannelHandlerContext ctx) {
//            this.ctx = requireNonNull(ctx);
//        }
//
//        public WithStatus withStatus(final HttpResponseStatus status) {
//            return new WithStatus(this, status);
//        }
//
//        public final class WithStatus {
//            private final ChannelHandlerContext ctx;
//            private final HttpResponseStatus status;
//
//            WithStatus(final ResponseOutput prev, final HttpResponseStatus status) {
//                ctx = prev.ctx;
//                this.status = requireNonNull(status);
//            }
//
//            public WithHeaders withHeaders(final HttpHeaders headers) {
//                return new WithHeaders(this, requireNonNull(headers));
//            }
//
//            public WithHeaders withoutHeaders() {
//                return new WithHeaders(this, DefaultHttpHeadersFactory.headersFactory().newEmptyHeaders());
//            }
//        }
//
//        public final class WithHeaders {
//            private final ChannelHandlerContext ctx;
//            private final HttpResponseStatus status;
//            private final HttpHeaders headers;
//
//            WithHeaders(final WithStatus prev, final HttpHeaders headers) {
//                ctx = prev.ctx;
//                status = prev.status;
//                this.headers = requireNonNull(headers);
//            }
//
//            public ResponseProof withBody(final ResponseBodyWriter writer) {
//
//                return ResponseProof.INSTANCE;
//            }
//
//            public ResponseProof withoutBody() {
//                // FIXME: build a FullHttpResponse and send it
//
//                return ResponseProof.INSTANCE;
//            }
//        }
//    }
//
//    final class ResponseProof {
//        static final ResponseProof INSTANCE = new ResponseProof();
//
//        private ResponseProof() {
//            // Hidden on purpose
//        }
//    }
}
