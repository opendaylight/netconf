/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import java.util.Objects;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.spi.EndpointConfiguration;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;

/**
 * Configuration of the JAX-RS server endpoint.
 */
public final class JaxRsEndpointConfiguration extends EndpointConfiguration {
    public static final @NonNull String DEFAULT_NAME_PREFIX = "ping-executor";
    public static final int DEFAULT_CORE_POOL_SIZE = 1;

    private final @NonNull String restconf;
    private final @NonNull String pingNamePrefix;
    private final int pingCorePoolSize;
    private final boolean restconfLoggingEnabled;
    private final boolean loggingHeadersEnabled;
    private final boolean loggingQueryParametersEnabled;
    private final boolean loggingBodyEnabled;
    private final @NonNull Set<String> hiddenHttpHeaders;

    public JaxRsEndpointConfiguration(final ErrorTagMapping errorTagMapping, final PrettyPrintParam prettyPrint,
            final Uint16 sseMaximumFragmentLength, final Uint32 sseHeartbeatIntervalMillis, final String restconf,
            final String pingNamePrefix, final int pingCorePoolSize, final boolean restconfLoggingEnabled,
            final boolean loggingHeadersEnabled, final boolean loggingQueryParametersEnabled,
            final boolean loggingBodyEnabled, final Set<String> hiddenHttpHeaders) {
        super(errorTagMapping, prettyPrint, sseMaximumFragmentLength, sseHeartbeatIntervalMillis);
        if (restconf.endsWith("/")) {
            throw new IllegalArgumentException("restconf cannot end with '/'");
        }
        this.restconf = restconf;
        this.pingNamePrefix = requireNonNull(pingNamePrefix);
        this.pingCorePoolSize = pingCorePoolSize;
        this.restconfLoggingEnabled = restconfLoggingEnabled;
        this.loggingHeadersEnabled = loggingHeadersEnabled;
        this.loggingQueryParametersEnabled = loggingQueryParametersEnabled;
        this.loggingBodyEnabled = loggingBodyEnabled;
        this.hiddenHttpHeaders = Set.copyOf(hiddenHttpHeaders);
    }

    public JaxRsEndpointConfiguration(final ErrorTagMapping errorTagMapping, final PrettyPrintParam prettyPrint,
            final Uint16 sseMaximumFragmentLength, final Uint32 sseHeartbeatIntervalMillis, final String restconf) {
        this(errorTagMapping, prettyPrint, sseMaximumFragmentLength, sseHeartbeatIntervalMillis, restconf,
            DEFAULT_NAME_PREFIX, DEFAULT_CORE_POOL_SIZE, false, false, false, false, Set.of());
    }

    /**
     * Return the value of {@code {+restconf}} macro. May be empty, guaranteed to not end with {@code /}.
     *
     * @return the value of {@code {+restconf}} macro
     */
    public @NonNull String restconf() {
        return restconf;
    }

    public @NonNull String pingNamePrefix() {
        return pingNamePrefix;
    }

    public int pingCorePoolSize() {
        return pingCorePoolSize;
    }

    /**
     * Enabled RESTCONF audit logs.
     *
     * @return {@code true}: turned on RESTCONF audit logs; {@code false}: RESTCONF audit logs are
     *     turned off irrespective of other settings
     */
    public boolean restconfLoggingEnabled() {
        return restconfLoggingEnabled;
    }

    /**
     * Including all HTTP headers in output logs.
     *
     * @return logs include all HTTP headers (both requests and responses)
     */
    public boolean loggingHeadersEnabled() {
        return loggingHeadersEnabled;
    }

    /**
     * Including all request query parameters in output logs.
     *
     * @return {@code true}: logs include all query parameters
     */
    public boolean loggingQueryParametersEnabled() {
        return loggingQueryParametersEnabled;
    }

    /**
     * Logging full HTTP request/response body.
     *
     * @return {@code true}: logs include request/response body
     */
    public boolean loggingBodyEnabled() {
        return loggingBodyEnabled;
    }

    /**
     * Get set of names that identify HTTP headers omitted from log output.
     *
     * @return {@link Set} of HTTP header identifiers
     */
    public @NonNull Set<String> hiddenHttpHeaders() {
        return hiddenHttpHeaders;
    }

    @Override
    public int hashCode() {
        return Objects.hash(errorTagMapping(), prettyPrint(), sseMaximumFragmentLength(), sseHeartbeatIntervalMillis(),
            restconf, pingNamePrefix, pingCorePoolSize, restconfLoggingEnabled, loggingHeadersEnabled,
            loggingQueryParametersEnabled, loggingBodyEnabled, hiddenHttpHeaders);
    }

    @Override
    public boolean equals(final Object obj) {
        return this == obj || obj instanceof JaxRsEndpointConfiguration other
            && errorTagMapping().equals(other.errorTagMapping()) && prettyPrint().equals(other.prettyPrint())
            && sseMaximumFragmentLength().equals(other.sseMaximumFragmentLength())
            && sseHeartbeatIntervalMillis().equals(other.sseHeartbeatIntervalMillis())
            && restconf.equals(other.restconf) && pingNamePrefix.equals(other.pingNamePrefix)
            && pingCorePoolSize == other.pingCorePoolSize && restconfLoggingEnabled == other.restconfLoggingEnabled
            && loggingHeadersEnabled == other.loggingHeadersEnabled
            && loggingQueryParametersEnabled == other.loggingQueryParametersEnabled
            && loggingBodyEnabled == other.loggingBodyEnabled && hiddenHttpHeaders.equals(other.hiddenHttpHeaders);
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper)
            .add("restconf", restconf)
            .add("pingNamePrefix", pingNamePrefix)
            .add("pingCorePoolSize", pingCorePoolSize)
            .add("restconfLoggingEnabled", restconfLoggingEnabled)
            .add("loggingHeadersEnabled", loggingHeadersEnabled)
            .add("loggingQueryParametersEnabled", loggingQueryParametersEnabled)
            .add("loggingBodyEnabled", loggingBodyEnabled)
            .add("hiddenHttpHeaders", hiddenHttpHeaders);
    }
}
