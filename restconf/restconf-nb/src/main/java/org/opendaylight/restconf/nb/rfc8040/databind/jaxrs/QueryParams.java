/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind.jaxrs;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.query.ChangedLeafNodesOnlyParam;
import org.opendaylight.restconf.api.query.ChildNodesOnlyParam;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.restconf.api.query.FieldsParam;
import org.opendaylight.restconf.api.query.FilterParam;
import org.opendaylight.restconf.api.query.InsertParam;
import org.opendaylight.restconf.api.query.LeafNodesOnlyParam;
import org.opendaylight.restconf.api.query.PointParam;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.api.query.SkipNotificationDataParam;
import org.opendaylight.restconf.api.query.StartTimeParam;
import org.opendaylight.restconf.api.query.StopTimeParam;
import org.opendaylight.restconf.api.query.WithDefaultsParam;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.nb.rfc8040.ReadDataParams;
import org.opendaylight.restconf.nb.rfc8040.legacy.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.legacy.QueryParameters;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.NetconfFieldsTranslator;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.WriterFieldsTranslator;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

@Beta
public final class QueryParams {
    private static final Set<String> KNOWN_PARAMS = Set.of(
        // Read data
        ContentParam.uriName, DepthParam.uriName, FieldsParam.uriName, WithDefaultsParam.uriName,
        PrettyPrintParam.uriName,
        // Modify data
        InsertParam.uriName, PointParam.uriName,
        // Notifications
        FilterParam.uriName, StartTimeParam.uriName, StopTimeParam.uriName,
        // ODL extensions
        LeafNodesOnlyParam.uriName, SkipNotificationDataParam.uriName, ChangedLeafNodesOnlyParam.uriName,
        ChildNodesOnlyParam.uriName);

    private QueryParams() {
        // Utility class
    }

    /**
     * Normalize query parameters from an {@link UriInfo}.
     *
     * @param uriInfo An {@link UriInfo}
     * @return Normalized query parameters
     * @throws NullPointerException if {@code uriInfo} is {@code null}
     * @throws IllegalArgumentException if there are multiple values for a parameter
     */
    public static @NonNull ImmutableMap<String, String> normalize(final UriInfo uriInfo) {
        final var builder = ImmutableMap.<String, String>builder();
        for (var entry : uriInfo.getQueryParameters().entrySet()) {
            final var values = entry.getValue();
            switch (values.size()) {
                case 0:
                    // No-op
                    break;
                case 1:
                    builder.put(entry.getKey(), values.get(0));
                    break;
                default:
                    throw new IllegalArgumentException(
                        "Parameter " + entry.getKey() + " can appear at most once in request URI");
            }
        }
        return builder.build();
    }

    public static QueryParameters newQueryParameters(final ReadDataParams params,
            final InstanceIdentifierContext identifier) {
        final var fields = params.fields();
        if (fields == null) {
            return QueryParameters.of(params);
        }

        return identifier.getMountPoint() != null
            ? QueryParameters.ofFieldPaths(params, NetconfFieldsTranslator.translate(identifier, fields))
                : QueryParameters.ofFields(params, WriterFieldsTranslator.translate(identifier, fields));
    }

    /**
     * Parse parameters from URI request and check their types and values.
     *
     * @param uriInfo    URI info
     * @return {@link ReadDataParams}
     */
    public static @NonNull ReadDataParams newReadDataParams(final UriInfo uriInfo) {
        ContentParam content = ContentParam.ALL;
        DepthParam depth = null;
        FieldsParam fields = null;
        WithDefaultsParam withDefaults = null;
        PrettyPrintParam prettyPrint = null;

        for (var entry : uriInfo.getQueryParameters().entrySet()) {
            final var paramName = entry.getKey();
            final var paramValues = entry.getValue();

            try {
                switch (paramName) {
                    case ContentParam.uriName:
                        content = optionalParam(ContentParam::forUriValue, paramName, paramValues);
                        break;
                    case DepthParam.uriName:
                        final String depthStr = optionalParam(paramName, paramValues);
                        try {
                            depth = DepthParam.forUriValue(depthStr);
                        } catch (IllegalArgumentException e) {
                            throw new RestconfDocumentedException(e, new RestconfError(ErrorType.PROTOCOL,
                                ErrorTag.INVALID_VALUE, "Invalid depth parameter: " + depthStr, null,
                                "The depth parameter must be an integer between 1 and 65535 or \"unbounded\""));
                        }
                        break;
                    case FieldsParam.uriName:
                        fields = optionalParam(FieldsParam::forUriValue, paramName, paramValues);
                        break;
                    case WithDefaultsParam.uriName:
                        withDefaults = optionalParam(WithDefaultsParam::forUriValue, paramName, paramValues);
                        break;
                    case PrettyPrintParam.uriName:
                        prettyPrint = optionalParam(PrettyPrintParam::forUriValue, paramName, paramValues);
                        break;
                    default:
                        throw unhandledParam("read", paramName);
                }
            } catch (IllegalArgumentException e) {
                throw new RestconfDocumentedException("Invalid " + paramName + " value: " + e.getMessage(),
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE, e);
            }
        }

        return new ReadDataParams(content, depth, fields, withDefaults, prettyPrint);
    }

    private static RestconfDocumentedException unhandledParam(final String operation, final String name) {
        return KNOWN_PARAMS.contains(name)
            ? new RestconfDocumentedException("Invalid parameter in " + operation + ": " + name,
                ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE)
            : new RestconfDocumentedException("Unknown parameter in " + operation + ": " + name,
                ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ATTRIBUTE);
    }

    @VisibleForTesting
    static @Nullable String optionalParam(final String name, final List<String> values) {
        return switch (values.size()) {
            case 0 -> null;
            case 1 -> requireNonNull(values.get(0));
            default -> throw new RestconfDocumentedException(
                "Parameter " + name + " can appear at most once in request URI",
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        };
    }

    private static <T> @Nullable T optionalParam(final Function<String, @NonNull T> factory, final String name,
            final List<String> values) {
        final String str = optionalParam(name, values);
        return str == null ? null : factory.apply(str);
    }
}
