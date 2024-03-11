/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.util.AsciiString;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.query.PrettyPrintParam;

interface RequestContext {

    @NonNull HttpMethod method();

    @Nullable AsciiString contentType();

    @NonNull String basePath();

    @NonNull String contextPath();

    @NonNull Map<String, List<String>> queryParameters();

    @NonNull InputStream requestBody();

    @NonNull FullHttpRequest request();

    @NonNull FutureCallback<FullHttpResponse> callback();

    boolean hasContextPath();

    @NonNull PrettyPrintParam defaultPrettyPrint();

    @NonNull AsciiString defaultContentType();
}
