/*
 * Copyright © 2021 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers.logging;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import java.util.Set;

/**
 * RESTCONF RFC-8040 logging configuration.
 */
public final class RestconfLoggingConfiguration {
    private final boolean restconfLoggingEnabled;
    private final boolean loggingHeadersEnabled;
    private final boolean loggingQueryParametersEnabled;
    private final boolean loggingBodyEnabled;
    private final Set<String> hiddenHttpHeaders;

    /**
     * Creation of RESTCONF logging configuration.
     *
     * @param restconfLoggingEnabled        {@code true}: turned on RESTCONF audit logs; {@code false}:
     *                                      RESTCONF audit logs are turned off irrespective of other settings
     * @param loggingHeadersEnabled         {@code true}: logs include all HTTP headers (both requests and responses)
     * @param loggingQueryParametersEnabled {@code true}: logs include all query parameters
     * @param loggingBodyEnabled            {@code true}: logs include request/response body
     * @param hiddenHttpHeaders             list of names that identify HTTP headers omitted from log output;
     *                                      names of HTTP headers must be separated by ',' character
     */
    public RestconfLoggingConfiguration(final boolean restconfLoggingEnabled, final boolean loggingHeadersEnabled,
                                        final boolean loggingQueryParametersEnabled, final boolean loggingBodyEnabled,
                                        final String hiddenHttpHeaders) {
        this.restconfLoggingEnabled = restconfLoggingEnabled;
        this.loggingHeadersEnabled = loggingHeadersEnabled;
        this.loggingQueryParametersEnabled = loggingQueryParametersEnabled;
        this.loggingBodyEnabled = loggingBodyEnabled;
        this.hiddenHttpHeaders =  ImmutableSet.copyOf(hiddenHttpHeaders.split(","));
    }

    /**
     * Enabled RESTCONF audit logs.
     *
     * @return {@code true}: turned on RESTCONF audit logs; {@code false}: RESTCONF audit logs are
     *     turned off irrespective of other settings
     */
    public boolean isRestconfLoggingEnabled() {
        return restconfLoggingEnabled;
    }

    /**
     * Including all HTTP headers in output logs.
     *
     * @return logs include all HTTP headers (both requests and responses)
     */
    public boolean isLoggingHeadersEnabled() {
        return loggingHeadersEnabled;
    }

    /**
     * Including all request query parameters in output logs.
     *
     * @return {@code true}: logs include all query parameters
     */
    public boolean isLoggingQueryParametersEnabled() {
        return loggingQueryParametersEnabled;
    }

    /**
     * Logging full HTTP request/response body.
     *
     * @return {@code true}: logs include request/response body
     */
    public boolean isLoggingBodyEnabled() {
        return loggingBodyEnabled;
    }

    /**
     * Get set of names that identify HTTP headers omitted from log output.
     *
     * @return {@link Set} of HTTP header identifiers
     */
    public Set<String> getHiddenHttpHeaders() {
        return hiddenHttpHeaders;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("restconfLoggingEnabled", restconfLoggingEnabled)
                .add("loggingHeadersEnabled", loggingHeadersEnabled)
                .add("loggingQueryParametersEnabled", loggingQueryParametersEnabled)
                .add("loggingBodyEnabled", loggingBodyEnabled)
                .add("hiddenHttpHeaders", hiddenHttpHeaders)
                .toString();
    }
}