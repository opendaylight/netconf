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
import org.opendaylight.restconf.nb.rfc8040.ContentParameter;
import org.opendaylight.restconf.nb.rfc8040.DepthParameter;
import org.opendaylight.restconf.nb.rfc8040.FieldsParameter;
import org.opendaylight.restconf.nb.rfc8040.FilterParameter;
import org.opendaylight.restconf.nb.rfc8040.InsertParameter;
import org.opendaylight.restconf.nb.rfc8040.NotificationQueryParams;
import org.opendaylight.restconf.nb.rfc8040.PointParameter;
import org.opendaylight.restconf.nb.rfc8040.StartTimeParameter;
import org.opendaylight.restconf.nb.rfc8040.StopTimeParameter;
import org.opendaylight.restconf.nb.rfc8040.WithDefaultsParameter;
import org.opendaylight.restconf.nb.rfc8040.WriteDataParams;
import org.opendaylight.restconf.nb.rfc8040.legacy.QueryParameters;
import org.opendaylight.restconf.nb.rfc8040.legacy.QueryParameters.Builder;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

@Beta
public final class QueryParams {
    private static final Set<String> ALLOWED_PARAMETERS = Set.of(ContentParameter.uriName(), DepthParameter.uriName(),
        FieldsParameter.uriName(), WithDefaultsParameter.uriName());
    private static final List<String> POSSIBLE_CONTENT = Arrays.stream(ContentParameter.values())
        .map(ContentParameter::uriValue)
        .collect(Collectors.toUnmodifiableList());
    private static final List<String> POSSIBLE_WITH_DEFAULTS = Arrays.stream(WithDefaultsParameter.values())
        .map(WithDefaultsParameter::uriValue)
        .collect(Collectors.toUnmodifiableList());

    private QueryParams() {
        // Utility class
    }

    public static @NonNull NotificationQueryParams newNotificationQueryParams(final UriInfo uriInfo) {
        StartTimeParameter startTime = null;
        StopTimeParameter stopTime = null;
        FilterParameter filter = null;
        boolean skipNotificationData = false;

        for (final Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
            final String paramName = entry.getKey();
            final List<String> paramValues = entry.getValue();

            try {
                if (paramName.equals(StartTimeParameter.uriName())) {
                    startTime = optionalParam(StartTimeParameter::forUriValue, paramName, paramValues);
                    break;
                } else if (paramName.equals(StopTimeParameter.uriName())) {
                    stopTime = optionalParam(StopTimeParameter::forUriValue, paramName, paramValues);
                    break;
                } else if (paramName.equals(FilterParameter.uriName())) {
                    filter = optionalParam(FilterParameter::forUriValue, paramName, paramValues);
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

    /**
     * Parse parameters from URI request and check their types and values.
     *
     * @param identifier {@link InstanceIdentifierContext}
     * @param uriInfo    URI info
     * @return {@link QueryParameters}
     */
    public static QueryParameters newReadDataParams(final InstanceIdentifierContext<?> identifier,
                                                    final UriInfo uriInfo) {
        if (uriInfo == null) {
            return QueryParameters.empty();
        }

        // check only allowed parameters
        final MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
        checkParametersTypes(queryParams.keySet(), ALLOWED_PARAMETERS);

        final Builder builder = QueryParameters.builder();
        // check and set content
        final String contentStr = getSingleParameter(queryParams, ContentParameter.uriName());
        if (contentStr != null) {
            builder.setContent(RestconfDocumentedException.throwIfNull(
                ContentParameter.forUriValue(contentStr), ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
                "Invalid content parameter: %s, allowed values are %s", contentStr, POSSIBLE_CONTENT));
        }

        // check and set depth
        final String depthStr = getSingleParameter(queryParams, DepthParameter.uriName());
        if (depthStr != null) {
            try {
                builder.setDepth(DepthParameter.forUriValue(depthStr));
            } catch (IllegalArgumentException e) {
                throw new RestconfDocumentedException(e, new RestconfError(ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
                    "Invalid depth parameter: " + depthStr, null,
                    "The depth parameter must be an integer between 1 and 65535 or \"unbounded\""));
            }
        }

        // check and set fields
        final String fieldsStr = getSingleParameter(queryParams, FieldsParameter.uriName());
        if (fieldsStr != null) {
            // FIXME: parse a FieldsParameter instead
            if (identifier.getMountPoint() != null) {
                builder.setFieldPaths(parseFieldsPaths(identifier, fieldsStr));
            } else {
                builder.setFields(parseFieldsParameter(identifier, fieldsStr));
            }
        }

        // check and set withDefaults parameter
        final String withDefaultsStr = getSingleParameter(queryParams, WithDefaultsParameter.uriName());
        if (withDefaultsStr != null) {
            final WithDefaultsParameter val = WithDefaultsParameter.forUriValue(withDefaultsStr);
            if (val == null) {
                throw new RestconfDocumentedException(new RestconfError(ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
                    "Invalid with-defaults parameter: " + withDefaultsStr, null,
                    "The with-defaults parameter must be a string in " + POSSIBLE_WITH_DEFAULTS));
            }

            switch (val) {
                case REPORT_ALL:
                    break;
                case REPORT_ALL_TAGGED:
                    builder.setTagged(true);
                    break;
                default:
                    builder.setWithDefault(val);
            }
        }

        return builder.build();
    }

    public static @NonNull WriteDataParams newWriteDataParams(final UriInfo uriInfo) {
        InsertParameter insert = null;
        PointParameter point = null;

        for (final Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
            final String uriName = entry.getKey();
            final List<String> paramValues = entry.getValue();
            if (uriName.equals(InsertParameter.uriName())) {
                final String str = optionalParam(uriName, paramValues);
                if (str != null) {
                    insert = InsertParameter.forUriValue(str);
                    if (insert == null) {
                        throw new RestconfDocumentedException("Unrecognized insert parameter value '" + str + "'",
                            ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT);
                    }
                }
            } else if (PointParameter.uriName().equals(uriName)) {
                final String str = optionalParam(uriName, paramValues);
                if (str != null) {
                    point = PointParameter.forUriValue(str);
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
