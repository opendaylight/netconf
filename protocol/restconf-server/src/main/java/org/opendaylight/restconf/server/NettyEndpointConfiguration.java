/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import io.netty.util.AsciiString;
import java.util.Objects;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.spi.EndpointConfiguration;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;

/**
 * Configuration of the JAX-RS server endpoint.
 */
public final class NettyEndpointConfiguration extends EndpointConfiguration {
    public static final String JSON = "json";
    public static final String XML = "xml";

    private final @NonNull HttpServerStackGrouping transportConfiguration;
    private final @NonNull String groupName;
    private final int groupThreads;
    private final @NonNull String restconf;
    private final @NonNull AsciiString defaultAcceptType;

    public NettyEndpointConfiguration(final ErrorTagMapping errorTagMapping, final PrettyPrintParam prettyPrint,
            final Uint16 sseMaximumFragmentLength, final Uint32 sseHeartbeatIntervalMillis,
            final String restconf, final String groupName, final int groupThreads, final String defaultEncoding,
            final HttpServerStackGrouping transportConfiguration) {
        super(errorTagMapping, prettyPrint, sseMaximumFragmentLength, sseHeartbeatIntervalMillis);
        this.groupName = requireNonNull(groupName);
        this.groupThreads = groupThreads;
        this.transportConfiguration = requireNonNull(transportConfiguration);
        if (restconf.endsWith("/") || restconf.startsWith("/")) {
            throw new IllegalArgumentException("restconf cannot start or end with '/'");
        }
        this.restconf = restconf;
        if (JSON.equalsIgnoreCase(defaultEncoding)) {
            defaultAcceptType = NettyMediaTypes.APPLICATION_YANG_DATA_JSON;
        } else if (XML.equalsIgnoreCase(defaultEncoding)) {
            defaultAcceptType = NettyMediaTypes.APPLICATION_YANG_DATA_XML;
        } else {
            throw new IllegalArgumentException("defaultEncoding should either '%s' or '%s'".formatted(XML, JSON));
        }
    }

    /**
     * Return the value of {@code {+restconf}} macro. May be empty, guaranteed to not end with {@code /}.
     *
     * @return the value of {@code {+restconf}} macro
     */
    public @NonNull String restconf() {
        return restconf;
    }

    public @NonNull String groupName() {
        return groupName;
    }

    public int groupThreads() {
        return groupThreads;
    }

    public @NonNull HttpServerStackGrouping transportConfiguration() {
        return transportConfiguration;
    }

    public @NonNull AsciiString defaultAcceptType() {
        return defaultAcceptType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(errorTagMapping(), prettyPrint(), sseMaximumFragmentLength(), sseHeartbeatIntervalMillis(),
            restconf, groupName, groupThreads, transportConfiguration, defaultAcceptType);
    }

    @Override
    public boolean equals(final Object obj) {
        return this == obj || obj instanceof NettyEndpointConfiguration other
            && errorTagMapping().equals(other.errorTagMapping()) && prettyPrint().equals(other.prettyPrint())
            && sseMaximumFragmentLength().equals(other.sseMaximumFragmentLength())
            && sseHeartbeatIntervalMillis().equals(other.sseHeartbeatIntervalMillis())
            && restconf.equals(other.restconf)
            && groupName.equals(other.groupName)
            && groupThreads == other.groupThreads
            && transportConfiguration.equals(other.transportConfiguration)
            && defaultAcceptType.equals(other.defaultAcceptType);
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper)
            .add("restconf", restconf)
            .add("groupName", groupName)
            .add("groupThreads", groupThreads)
            .add("defaultAcceptType", defaultAcceptType)
            .add("transportConfiguration", transportConfiguration);
    }
}
