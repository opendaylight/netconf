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
import java.net.URI;
import java.util.Objects;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.spi.EndpointConfiguration;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;

/**
 * Configuration of the Netty RESTCONF server endpoint.
 */
public final class NettyEndpointConfiguration extends EndpointConfiguration {
    private final @NonNull HttpServerStackGrouping transportConfiguration;
    private final @NonNull String groupName;
    private final int groupThreads;
    private final @NonNull URI baseUri;
    private final @NonNull AsciiString defaultAcceptType;

    public NettyEndpointConfiguration(final ErrorTagMapping errorTagMapping, final PrettyPrintParam prettyPrint,
            final Uint16 sseMaximumFragmentLength, final Uint32 sseHeartbeatIntervalMillis,
            final URI baseUri, final String groupName, final int groupThreads,
            final Encoding defaultEncoding, final HttpServerStackGrouping transportConfiguration) {
        super(errorTagMapping, prettyPrint, sseMaximumFragmentLength, sseHeartbeatIntervalMillis);
        this.groupName = requireNonNull(groupName);
        this.groupThreads = groupThreads;
        this.transportConfiguration = requireNonNull(transportConfiguration);
        this.baseUri = requireNonNull(baseUri);
        this.defaultAcceptType = requireNonNull(defaultEncoding).mediaType();
    }

    public @NonNull URI baseUri() {
        return baseUri;
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
            baseUri, groupName, groupThreads, transportConfiguration, defaultAcceptType);
    }

    @Override
    public boolean equals(final Object obj) {
        return this == obj || obj instanceof NettyEndpointConfiguration other
            && errorTagMapping().equals(other.errorTagMapping()) && prettyPrint().equals(other.prettyPrint())
            && sseMaximumFragmentLength().equals(other.sseMaximumFragmentLength())
            && sseHeartbeatIntervalMillis().equals(other.sseHeartbeatIntervalMillis())
            && baseUri.equals(other.baseUri)
            && groupName.equals(other.groupName)
            && groupThreads == other.groupThreads
            && transportConfiguration.equals(other.transportConfiguration)
            && defaultAcceptType.equals(other.defaultAcceptType);
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper)
            .add("restconf", baseUri)
            .add("groupName", groupName)
            .add("groupThreads", groupThreads)
            .add("defaultAcceptType", defaultAcceptType)
            .add("transportConfiguration", transportConfiguration);
    }

    public enum Encoding {
        XML("xml", NettyMediaTypes.APPLICATION_YANG_DATA_XML),
        JSON("json", NettyMediaTypes.APPLICATION_YANG_DATA_JSON);

        private final String id;
        private final AsciiString mediaType;

        Encoding(final String id, final AsciiString mediaType) {
            this.id = id;
            this.mediaType = mediaType;
        }

        public AsciiString mediaType() {
            return mediaType;
        }

        public static Encoding from(final String value) {
            requireNonNull(value);
            for (var encoding : values()) {
                if (encoding.id.equalsIgnoreCase(value)) {
                    return encoding;
                }
            }
            throw new IllegalArgumentException("Unsupported encoding value");
        }
    }
}
