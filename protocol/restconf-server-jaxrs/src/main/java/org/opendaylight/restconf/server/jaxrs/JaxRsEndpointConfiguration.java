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

    public JaxRsEndpointConfiguration(final ErrorTagMapping errorTagMapping, final PrettyPrintParam prettyPrint,
            final Uint16 sseMaximumFragmentLength, final Uint32 sseHeartbeatIntervalMillis, final String restconf,
            final String pingNamePrefix, final int pingCorePoolSize) {
        super(errorTagMapping, prettyPrint, sseMaximumFragmentLength, sseHeartbeatIntervalMillis);
        if (restconf.endsWith("/")) {
            throw new IllegalArgumentException("restconf cannot end with '/'");
        }
        this.restconf = restconf;
        this.pingNamePrefix = requireNonNull(pingNamePrefix);
        this.pingCorePoolSize = pingCorePoolSize;
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

    @Override
    public int hashCode() {
        return Objects.hash(errorTagMapping(), prettyPrint(), sseMaximumFragmentLength(), sseHeartbeatIntervalMillis(),
            restconf, pingNamePrefix, pingCorePoolSize);
    }

    @Override
    public boolean equals(final Object obj) {
        return this == obj || obj instanceof JaxRsEndpointConfiguration other
            && errorTagMapping().equals(other.errorTagMapping()) && prettyPrint().equals(other.prettyPrint())
            && sseMaximumFragmentLength().equals(other.sseMaximumFragmentLength())
            && sseHeartbeatIntervalMillis().equals(other.sseHeartbeatIntervalMillis())
            && restconf.equals(other.restconf) && pingNamePrefix.equals(other.pingNamePrefix)
            && pingCorePoolSize == other.pingCorePoolSize;
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper)
            .add("restconf", restconf)
            .add("pingNamePrefix", pingNamePrefix)
            .add("pingCorePoolSize", pingCorePoolSize);
    }
}
