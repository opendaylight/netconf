/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http.rfc6415;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.AbstractRequestResponse;
import org.opendaylight.netconf.transport.http.WellKnownURI;

/**
 * Abstract based class for {@link HostMeta} and {@link HostMetaJson}.
 */
public abstract sealed class AbstractHostMeta extends AbstractRequestResponse implements WellKnownURI.Suffix, XRD
        permits HostMeta, HostMetaJson {
    private final Map<URI, Link> links;

    AbstractHostMeta(final Map<URI, Link> links) {
        super(HttpResponseStatus.OK, null);
        this.links = requireNonNull(links);
    }

    @Override
    public final Stream<? extends Link> links() {
        return links.values().stream().sorted(Comparator.comparing(Link::rel));
    }

    @Override
    public final @Nullable Link lookupLink(final URI rel) {
        return links.get(requireNonNull(rel));
    }

    @Override
    public ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper).add("links", links());
    }
}
