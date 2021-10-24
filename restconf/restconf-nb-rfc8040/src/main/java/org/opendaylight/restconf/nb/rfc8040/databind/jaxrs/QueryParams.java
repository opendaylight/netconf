/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind.jaxrs;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserFieldsParameter.parseFieldsParameter;
import static org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserFieldsParameter.parseFieldsPaths;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.ws.rs.core.MultivaluedMap;
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
import org.opendaylight.restconf.nb.rfc8040.NotificationQueryParams;
import org.opendaylight.restconf.nb.rfc8040.PointParam;
import org.opendaylight.restconf.nb.rfc8040.ReadDataParams;
import org.opendaylight.restconf.nb.rfc8040.StartTimeParam;
import org.opendaylight.restconf.nb.rfc8040.StopTimeParam;
import org.opendaylight.restconf.nb.rfc8040.WithDefaultsParam;
import org.opendaylight.restconf.nb.rfc8040.WriteDataParams;
import org.opendaylight.restconf.nb.rfc8040.legacy.QueryParameters;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

@Beta
public final class QueryParams {
    private static final Set<String> ALLOWED_PARAMETERS = Set.of(ContentParam.uriName(), DepthParam.uriName(),
        FieldsParam.uriName(), WithDefaultsParam.uriName());
    private static final List<String> POSSIBLE_CONTENT = Arrays.stream(ContentParam.values())
        .map(ContentParam::uriValue)
        .collect(Collectors.toUnmodifiableList());
    private static final List<String> POSSIBLE_WITH_DEFAULTS = Arrays.stream(WithDefaultsParam.values())
        .map(WithDefaultsParam::uriValue)
        .collect(Collectors.toUnmodifiableList());

    private QueryParams() {
        // Utility class
    }

    public static @NonNull NotificationQueryParams newNotificationQueryParams(final UriInfo uriInfo) {
        StartTimeParam startTime = null;
        StopTimeParam stopTime = null;
        FilterParam filter = null;
        boolean skipNotificationData = false;

        for (final Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
            final String paramName = entry.getKey();
            final List<String> paramValues = entry.getValue();

            try {
                if (paramName.equals(StartTimeParam.uriName())) {
                    startTime = optionalParam(StartTimeParam::forUriValue, paramName, paramValues);
                    break;
                } else if (paramName.equals(StopTimeParam.uriName())) {
                    stopTime = optionalParam(StopTimeParam::forUriValue, paramName, paramValues);
                    break;
                } else if (paramName.equals(FilterParam.uriName())) {
                    filter = optionalParam(FilterParam::forUriValue, paramName, paramValues);
                } else if (paramName.equals("odl-skip-notification-data")) {
                    // FIXME: this should be properly encapsulated in SkipNotificatioDataParameter
                    skipNotificationData = Boolean.parseBoolean(optionalParam(paramName, paramValues));
                } else {
                    throw new RestconfDocumentedException("Bad parameter used with notifications: " + paramName);
                }
            } catch (IllegalArgumentException e) {
                throw new RestconfDocumentedException("Invalid " + paramName + " value: " + e.getMessage(), e);
            }
        }

        try {
            return NotificationQueryParams.of(startTime, stopTime, filter, skipNotificationData);
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
            ? QueryParameters.ofFieldPaths(params, parseFieldsPaths(identifier, fields.uriValue()))
                : QueryParameters.ofFields(params, parseFieldsParameter(identifier, fields.uriValue()));
    }

    /**
     * Parse parameters from URI request and check their types and values.
     *
     * @param uriInfo    URI info
     * @return {@link ReadDataParams}
     */
    public static @NonNull ReadDataParams newReadDataParams(final UriInfo uriInfo) {
        // check only allowed parameters
        final MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
        checkParametersTypes(queryParams.keySet(), ALLOWED_PARAMETERS);

        // check and set content
        final String contentStr = getSingleParameter(queryParams, ContentParam.uriName());
        final ContentParam content = contentStr == null ? ContentParam.ALL
            : RestconfDocumentedException.throwIfNull(ContentParam.forUriValue(contentStr),
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
                "Invalid content parameter: %s, allowed values are %s", contentStr, POSSIBLE_CONTENT);

        // check and set depth
        final DepthParam depth;
        final String depthStr = getSingleParameter(queryParams, DepthParam.uriName());
        if (depthStr != null) {
            try {
                depth = DepthParam.forUriValue(depthStr);
            } catch (IllegalArgumentException e) {
                throw new RestconfDocumentedException(e, new RestconfError(ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
                    "Invalid depth parameter: " + depthStr, null,
                    "The depth parameter must be an integer between 1 and 65535 or \"unbounded\""));
            }
        } else {
            depth = null;
        }

        // check and set fields
        final FieldsParam fields;
        final String fieldsStr = getSingleParameter(queryParams, FieldsParam.uriName());
        if (fieldsStr != null) {
            try {
                fields = FieldsParam.parse(fieldsStr);
            } catch (ParseException e) {
                throw new RestconfDocumentedException(e, new RestconfError(ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
                    "Invalid filds parameter: " + fieldsStr));
            }
        } else {
            fields = null;
        }

        // check and set withDefaults parameter
        final WithDefaultsParam withDefaults;
        final boolean tagged;

        final String withDefaultsStr = getSingleParameter(queryParams, WithDefaultsParam.uriName());
        if (withDefaultsStr != null) {
            final WithDefaultsParam val = WithDefaultsParam.forUriValue(withDefaultsStr);
            if (val == null) {
                throw new RestconfDocumentedException(new RestconfError(ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
                    "Invalid with-defaults parameter: " + withDefaultsStr, null,
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
        } else {
            withDefaults = null;
            tagged = false;
        }

        // FIXME: recognize pretty-print here
        return ReadDataParams.of(content, depth, fields, withDefaults, tagged, false);
    }

    public static @NonNull WriteDataParams newWriteDataParams(final UriInfo uriInfo) {
        InsertParam insert = null;
        PointParam point = null;

        for (final Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
            final String uriName = entry.getKey();
            final List<String> paramValues = entry.getValue();
            if (uriName.equals(InsertParam.uriName())) {
                final String str = optionalParam(uriName, paramValues);
                if (str != null) {
                    insert = InsertParam.forUriValue(str);
                    if (insert == null) {
                        throw new RestconfDocumentedException("Unrecognized insert parameter value '" + str + "'",
                            ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT);
                    }
                }
            } else if (PointParam.uriName().equals(uriName)) {
                final String str = optionalParam(uriName, paramValues);
                if (str != null) {
                    point = PointParam.forUriValue(str);
                }
            } else {
                throw new RestconfDocumentedException("Bad parameter for post: " + uriName,
                    ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT);
            }
        }

        try {
            return WriteDataParams.of(insert, point);
        } catch (IllegalArgumentException e) {
            throw new RestconfDocumentedException("Invalid query parameters: " + e.getMessage(), e);
        }
    }

    /**
     * Check if URI does not contain not allowed parameters for specified operation.
     *
     * @param usedParameters parameters used in URI request
     * @param allowedParameters allowed parameters for operation
     */
    @VisibleForTesting
    static void checkParametersTypes(final Set<String> usedParameters, final Set<String> allowedParameters) {
        if (!allowedParameters.containsAll(usedParameters)) {
            final Set<String> notAllowedParameters = usedParameters.stream()
                .filter(param -> !allowedParameters.contains(param))
                .collect(Collectors.toSet());
            throw new RestconfDocumentedException("Not allowed parameters for read operation: " + notAllowedParameters,
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }
    }

    @VisibleForTesting
    static @Nullable String getSingleParameter(final MultivaluedMap<String, String> params, final String name) {
        final var values = params.get(name);
        return values == null ? null : optionalParam(name, values);
    }

    private static @Nullable String optionalParam(final String name, final List<String> values) {
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
