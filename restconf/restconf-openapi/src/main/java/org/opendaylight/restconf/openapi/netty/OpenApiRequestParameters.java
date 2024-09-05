/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.netty;

import com.google.common.collect.Maps;
import io.netty.handler.codec.http.QueryStringDecoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

final class OpenApiRequestParameters {

    private final Map<Param, String> parametersMap;
    private final RequestType requestType;

    public OpenApiRequestParameters(final String basePath, final String uri) {
        final var decoder = new QueryStringDecoder(uri, StandardCharsets.UTF_8);
        final var uriPath = decoder.path();
        if (uriPath.startsWith(basePath)) {
            final var paramMap = new EnumMap<Param, String>(Param.class);
            mapParameters(decoder.parameters(), paramMap);
            this.requestType = parsePath(uriPath.substring(basePath.length()), paramMap);
            this.parametersMap = Maps.immutableEnumMap(paramMap);
        } else {
            this.requestType = RequestType.UNKNOWN;
            this.parametersMap = Map.of();
        }
    }

    RequestType requestType() {
        return requestType;
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
                if (key != null && value != null){
                    paramsMap.put(key, value);
                }
            }
        });
    }

    private static RequestType parsePath(final String path, final Map<Param, String> paramMap) {
        for (var requestType : RequestType.values()) {
            if (Objects.equals(path, requestType.path)) {
                // exact match
                return requestType;
            }
            if (requestType.pathPattern != null) {
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
        HOME("/ui"),
        STATIC("(/explorer/.+)", Param.RESOURCE),
        SINGLE("/api/v3/single"),
        SINGLE_META("/api/v3/single/meta"),
        MODULE("/api/v3/([^/]+)", Param.MODULE),
        MOUNTS("/api/v3/mounts"),
        MOUNTS_INSTANCE("/api/v3/mounts/([^/]+)", Param.INSTANCE),
        MOUNTS_INSTANCE_META("/api/v3/mounts/([^/]+)/meta", Param.INSTANCE),
        MOUNTS_INSTANCE_MODULE("/api/v3/mounts/([^/]+)/([^/]+)", Param.INSTANCE, Param.MODULE),
        UNKNOWN(null);

        @Nullable
        private final String path;
        @Nullable
        private final Param[] pathParams;
        @Nullable
        private final Pattern pathPattern;

        RequestType(final String exactPath) {
            this.path = exactPath;
            this.pathPattern = null;
            this.pathParams = null;
        }

        RequestType(final String pathRegex, final Param... pathParams) {
            this.path = null;
            this.pathPattern = Pattern.compile(pathRegex);
            this.pathParams = pathParams;
        }
    }
}
