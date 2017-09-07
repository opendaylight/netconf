/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.restful.utils;

import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;

/**
 * {@link Deprecated} move to splitted module restconf-nb-rfc8040.
 */
@Deprecated
class ParametersUtil {

    private ParametersUtil() {
        throw new UnsupportedOperationException("Util class.");
    }

    /**
     * Check if URI does not contain not allowed parameters for specified operation.
     *
     * @param operationType
     *             type of operation (READ, POST, PUT, DELETE...)
     * @param usedParameters
     *             parameters used in URI request
     * @param allowedParameters
     *             allowed parameters for operation
     */
    static void checkParametersTypes(@Nonnull final String operationType,
                                     @Nonnull final Set<String> usedParameters,
                                     @Nonnull final String... allowedParameters) {
        final Set<String> notAllowedParameters = Sets.newHashSet(usedParameters);
        notAllowedParameters.removeAll(Sets.newHashSet(allowedParameters));

        if (!notAllowedParameters.isEmpty()) {
            throw new RestconfDocumentedException(
                    "Not allowed parameters for " + operationType + " operation: " + notAllowedParameters,
                    RestconfError.ErrorType.PROTOCOL,
                    RestconfError.ErrorTag.INVALID_VALUE);
        }
    }

    /**
     * Check if URI does not contain value for the same parameter more than once.
     *
     * @param parameterValues
     *             URI parameter values
     * @param parameterName
     *             URI parameter name
     */
    static void checkParameterCount(@Nonnull final List<String> parameterValues, @Nonnull final String parameterName) {
        if (parameterValues.size() > 1) {
            throw new RestconfDocumentedException(
                    "Parameter " + parameterName + " can appear at most once in request URI",
                    ErrorType.PROTOCOL,
                    ErrorTag.INVALID_VALUE);
        }
    }
}
