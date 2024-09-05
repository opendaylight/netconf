/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.netty;

import com.google.common.collect.Maps;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.eclipse.jdt.annotation.NonNullByDefault;

final class OpenApiRequestParameters {

    private final Map<Param, String> parametersMap;
    private final RequestType requestType;
    private final HttpVersion protocolVersion;
    private final String etag;

    OpenApiRequestParameters(final String basePath, final FullHttpRequest request) {
        this.protocolVersion = request.protocolVersion();
        this.etag = request.headers().get(HttpHeaderNames.IF_NONE_MATCH, null);
        final var decoder = new QueryStringDecoder(request.uri(), StandardCharsets.UTF_8);
        final var uriPath = decoder.path();
        if (uriPath.startsWith(basePath)) {
            final var paramMap = new EnumMap<Param, String>(Param.class);
            mapParameters(decoder.parameters(), paramMap);
            var path = uriPath.endsWith("/")
                ? uriPath.substring(basePath.length(), uriPath.length() - 1) : uriPath.substring(basePath.length());
            this.requestType = parsePath(path, paramMap);
            this.parametersMap = Maps.immutableEnumMap(paramMap);
        } else {
            this.requestType = RequestType.UNKNOWN;
            this.parametersMap = Map.of();
        }
    }

    HttpVersion protocolVersion() {
        return protocolVersion;
    }

    String etag() {
        return etag;
    }

    RequestType requestType() {
        return requestType;
    }

    String resource() {
        return parametersMap.get(Param.RESOURCE);
    }

    String module() {
        return parametersMap.get(Param.MODULE);
    }

    String revision() {
        return parametersMap.get(Param.REVISION);
    }

    String instance() {
        return parametersMap.get(Param.INSTANCE);
    }

    Integer width() {
        return intParam(Param.WIDTH);
    }

    Integer depth() {
        return intParam(Param.DEPTH);
    }

    Integer limit() {
        return intParam(Param.LIMIT);
    }

    Integer offset() {
        return intParam(Param.OFFSET);
    }

    private Integer intParam(final Param param) {
        final var value = parametersMap.get(param);
        try {
            return value == null ? null : Integer.decode(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void mapParameters(final Map<String, List<String>> params, final Map<Param, String> paramsMap) {
        if (params == null || params.isEmpty()) {
            return;
        }
        params.forEach((name, valueList) -> {
            if (valueList != null && !valueList.isEmpty()) {
                final var value = valueList.getFirst();
                final var key = switch (name) {
                    case "revision" -> Param.REVISION;
                    case "width" -> Param.WIDTH;
                    case "depth" -> Param.DEPTH;
                    case "limit" -> Param.LIMIT;
                    case "offset" -> Param.OFFSET;
                    default -> null;
                };
                if (key != null && value != null) {
                    paramsMap.put(key, value);
                }
            }
        });
    }

    private static RequestType parsePath(final String path, final Map<Param, String> paramMap) {
        if (path.isEmpty()) {
            return RequestType.UNKNOWN;
        }
        for (var requestType : RequestType.values()) {
            if (requestType.exactPath.equals(path)) {
                // exact match
                return requestType;
            }
            if (requestType.pathParams.length > 0) {
                // try path regex with path params extraction
                final var matcher = requestType.pathPattern.matcher(path);
                if (matcher.matches()) {
                    // match by regex, map groups to params
                    var group = 1;
                    for (var param : requestType.pathParams) {
                        paramMap.put(param, matcher.group(group++));
                    }
                    return requestType;
                }
            }
        }
        return RequestType.UNKNOWN;
    }

    @NonNullByDefault
    private enum Param {
        RESOURCE, INSTANCE, MODULE, REVISION, WIDTH, DEPTH, LIMIT, OFFSET
    }

    @NonNullByDefault
    enum RequestType {
        UI("/ui"),
        ALT_UI("/api/v3/ui"),
        STATIC_CONTENT("(/explorer/.+)", Param.RESOURCE),
        SINGLE("/api/v3/single"),
        SINGLE_META("/api/v3/single/meta"),
        MOUNTS("/api/v3/mounts"),
        MODULE("/api/v3/([^/]+)", Param.MODULE),
        MOUNTS_INSTANCE("/api/v3/mounts/([^/]+)", Param.INSTANCE),
        MOUNTS_INSTANCE_META("/api/v3/mounts/([^/]+)/meta", Param.INSTANCE),
        MOUNTS_INSTANCE_MODULE("/api/v3/mounts/([^/]+)/([^/]+)", Param.INSTANCE, Param.MODULE),
        UNKNOWN("/");

        private final String exactPath;
        private final Param[] pathParams;
        private final Pattern pathPattern;

        RequestType(final String exactPath) {
            this.exactPath = exactPath;
            this.pathPattern = Pattern.compile(this.exactPath);
            this.pathParams = new Param[0];
        }

        RequestType(final String pathRegex, final Param... pathParams) {
            this.exactPath = "";
            this.pathPattern = Pattern.compile(pathRegex);
            this.pathParams = pathParams;
        }
    }
}
