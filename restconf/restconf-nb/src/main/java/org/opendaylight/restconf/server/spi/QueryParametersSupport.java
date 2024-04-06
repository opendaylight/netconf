/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.restconf.api.query.FieldsParam;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.api.query.WithDefaultsParam;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.server.api.DataGetParams;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

/**
 * Support utilities for dealing with {@link QueryParameters}.
 */
public final class QueryParametersSupport {
    private QueryParametersSupport() {
        // Hidden on purpose
    }

    public static @NonNull DataGetParams newDataGetParams(final QueryParameters params) {
        ContentParam content = ContentParam.ALL;
        DepthParam depth = null;
        FieldsParam fields = null;
        WithDefaultsParam withDefaults = null;
        PrettyPrintParam prettyPrint = params.getDefault(PrettyPrintParam.class);

        for (var entry : params.asCollection()) {
            final var name = entry.getKey();
            final var value = entry.getValue();

            switch (name) {
                case ContentParam.uriName:
                    content = parseParam(ContentParam::forUriValue, name, value);
                    break;
                case DepthParam.uriName:
                    try {
                        depth = DepthParam.forUriValue(value);
                    } catch (IllegalArgumentException e) {
                        throw new RestconfDocumentedException(e, new RestconfError(ErrorType.PROTOCOL,
                            ErrorTag.INVALID_VALUE, "Invalid depth parameter: " + value, null,
                            "The depth parameter must be an integer between 1 and 65535 or \"unbounded\""));
                    }
                    break;
                case FieldsParam.uriName:
                    fields = parseParam(FieldsParam::forUriValue, name, value);
                    break;
                case WithDefaultsParam.uriName:
                    withDefaults = parseParam(WithDefaultsParam::forUriValue, name, value);
                    break;
                case PrettyPrintParam.uriName:
                    prettyPrint = parseParam(PrettyPrintParam::forUriValue, name, value);
                    break;
                default:
                    throw new RestconfDocumentedException("Unknown parameter in /data GET: " + name,
                        ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ATTRIBUTE);
            }
        }

        return new DataGetParams(content, depth, fields, withDefaults, prettyPrint);
    }

    private static <T> @NonNull T parseParam(final Function<@NonNull String, @NonNull T> method, final String name,
            final @NonNull String value) {
        try {
            return method.apply(value);
        } catch (IllegalArgumentException e) {
            throw new RestconfDocumentedException("Invalid " + name + " value: " + e.getMessage(),
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE, e);
        }
    }
}
