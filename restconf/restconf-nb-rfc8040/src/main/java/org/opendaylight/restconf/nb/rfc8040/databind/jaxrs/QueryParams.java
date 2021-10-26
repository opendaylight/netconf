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
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.nb.rfc8040.ContentParam;
import org.opendaylight.restconf.nb.rfc8040.DepthParam;
import org.opendaylight.restconf.nb.rfc8040.FieldsParam;
import org.opendaylight.restconf.nb.rfc8040.FilterParam;
import org.opendaylight.restconf.nb.rfc8040.InsertParam;
import org.opendaylight.restconf.nb.rfc8040.LeafNodesOnlyParam;
import org.opendaylight.restconf.nb.rfc8040.NotificationQueryParams;
import org.opendaylight.restconf.nb.rfc8040.PointParam;
import org.opendaylight.restconf.nb.rfc8040.ReadDataParams;
import org.opendaylight.restconf.nb.rfc8040.SkipNotificationDataParam;
import org.opendaylight.restconf.nb.rfc8040.StartTimeParam;
import org.opendaylight.restconf.nb.rfc8040.StopTimeParam;
import org.opendaylight.restconf.nb.rfc8040.WithDefaultsParam;
import org.opendaylight.restconf.nb.rfc8040.WriteDataParams;
import org.opendaylight.restconf.nb.rfc8040.legacy.QueryParameters;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.NetconfFieldsTranslator;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.WriterFieldsTranslator;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

@Beta
public final class QueryParams {
    private static final List<String> POSSIBLE_CONTENT = Arrays.stream(ContentParam.values())
        .map(ContentParam::paramValue)
        .collect(Collectors.toUnmodifiableList());
    private static final List<String> POSSIBLE_WITH_DEFAULTS = Arrays.stream(WithDefaultsParam.values())
        .map(WithDefaultsParam::paramValue)
        .collect(Collectors.toUnmodifiableList());
    private static final Set<String> KNOWN_PARAMS = Set.of(
        // Read data
        ContentParam.uriName, DepthParam.uriName, FieldsParam.uriName, WithDefaultsParam.uriName,
        // Modify data
        InsertParam.uriName, PointParam.uriName,
        // Notifications
        FilterParam.uriName, StartTimeParam.uriName, StopTimeParam.uriName ,"odl-skip-notification-data");


    private QueryParams() {
        // Utility class
    }

    public static @NonNull NotificationQueryParams newNotificationQueryParams(final UriInfo uriInfo) {
        StartTimeParam startTime = null;
        StopTimeParam stopTime = null;
        FilterParam filter = null;
        LeafNodesOnlyParam leafNodesOnly = null;
        SkipNotificationDataParam skipNotificationData = null;

        for (Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
            final String paramName = entry.getKey();
            final List<String> paramValues = entry.getValue();

            try {
                switch (paramName) {
                    case FilterParam.uriName:
                        filter = optionalParam(FilterParam::forUriValue, paramName, paramValues);
                        break;
                    case StartTimeParam.uriName:
                        startTime = optionalParam(StartTimeParam::forUriValue, paramName, paramValues);
                        break;
                    case StopTimeParam.uriName:
                        stopTime = optionalParam(StopTimeParam::forUriValue, paramName, paramValues);
                        break;
                    case LeafNodesOnlyParam.uriName:
                        leafNodesOnly = optionalParam(LeafNodesOnlyParam::forUriValue, paramName, paramValues);
                        break;
                    case SkipNotificationDataParam.uriName:
                        skipNotificationData = optionalParam(SkipNotificationDataParam::forUriValue, paramName,
                            paramValues);
                        break;
                    default:
                        throw unhandledParam("notification", paramName);
                }
            } catch (IllegalArgumentException e) {
                throw new RestconfDocumentedException("Invalid " + paramName + " value: " + e.getMessage(),
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE, e);
            }
        }

        try {
            return NotificationQueryParams.of(startTime, stopTime, filter, leafNodesOnly, skipNotificationData);
        } catch (IllegalArgumentException e) {
            throw new RestconfDocumentedException("Invalid query parameters: " + e.getMessage(), e);
        }
    }

    public static QueryParameters newQueryParameters(final ReadDataParams params,
            final InstanceIdentifierContext<?> identifier) {
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
        boolean tagged = false;

        for (Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
            final String paramName = entry.getKey();
            final List<String> paramValues = entry.getValue();

            switch (paramName) {
                case ContentParam.uriName:
                    final String contentStr = optionalParam(paramName, paramValues);
                    if (contentStr != null) {
                        content = RestconfDocumentedException.throwIfNull(ContentParam.forUriValue(contentStr),
                            ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
                            "Invalid content parameter: %s, allowed values are %s", contentStr, POSSIBLE_CONTENT);
                    }
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
                    final String fieldsStr = optionalParam(paramName, paramValues);
                    if (fieldsStr != null) {
                        try {
                            fields = FieldsParam.parse(fieldsStr);
                        } catch (ParseException e) {
                            throw new RestconfDocumentedException(e, new RestconfError(ErrorType.PROTOCOL,
                                ErrorTag.INVALID_VALUE, "Invalid filds parameter: " + fieldsStr));
                        }
                    }
                    break;
                case WithDefaultsParam.uriName:
                    final String withDefaultsStr = optionalParam(paramName, paramValues);
                    if (withDefaultsStr != null) {
                        final WithDefaultsParam val = WithDefaultsParam.forUriValue(withDefaultsStr);
                        if (val == null) {
                            throw new RestconfDocumentedException(new RestconfError(ErrorType.PROTOCOL,
                                ErrorTag.INVALID_VALUE, "Invalid with-defaults parameter: " + withDefaultsStr, null,
                                "The with-defaults parameter must be a string in " + POSSIBLE_WITH_DEFAULTS));
                        }

                        switch (val) {
                            case REPORT_ALL:
                                withDefaults = null;
                                tagged = false;
                                break;
                            case REPORT_ALL_TAGGED:
                                withDefaults = null;
                                tagged = true;
                                break;
                            default:
                                withDefaults = val;
                                tagged = false;
                        }
                    }
                    break;
                default:
                    // FIXME: recognize pretty-print here
                    throw unhandledParam("read", paramName);
            }
        }

        return ReadDataParams.of(content, depth, fields, withDefaults, tagged, false);
    }

    public static @NonNull WriteDataParams newWriteDataParams(final UriInfo uriInfo) {
        InsertParam insert = null;
        PointParam point = null;

        for (final Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
            final String uriName = entry.getKey();
            final List<String> paramValues = entry.getValue();
            switch (uriName) {
                case InsertParam.uriName:
                    final String instartStr = optionalParam(uriName, paramValues);
                    if (instartStr != null) {
                        insert = InsertParam.forUriValue(instartStr);
                        if (insert == null) {
                            throw new RestconfDocumentedException(
                                "Unrecognized insert parameter value '" + instartStr + "'", ErrorType.PROTOCOL,
                                ErrorTag.BAD_ELEMENT);
                        }
                    }
                    break;
                case PointParam.uriName:
                    final String pointStr = optionalParam(uriName, paramValues);
                    if (pointStr != null) {
                        point = PointParam.forUriValue(pointStr);
                    }
                    break;
                default:
                    throw unhandledParam("write", uriName);
            }
        }

        try {
            return WriteDataParams.of(insert, point);
        } catch (IllegalArgumentException e) {
            throw new RestconfDocumentedException("Invalid query parameters: " + e.getMessage(), e);
        }
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
        switch (values.size()) {
            case 0:
                return null;
            case 1:
                return requireNonNull(values.get(0));
            default:
                throw new RestconfDocumentedException("Parameter " + name + " can appear at most once in request URI",
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }
    }

    private static <T> @Nullable T optionalParam(final Function<String, @NonNull T> factory, final String name,
            final List<String> values) {
        final String str = optionalParam(name, values);
        return str == null ? null : factory.apply(str);
    }
}
