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
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AsciiString;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.ByteBufResponse;
import org.opendaylight.netconf.transport.http.ByteStreamRequestResponse;
import org.opendaylight.netconf.transport.http.WellKnownURI;

/**
 * Abstract based class for {@link HostMeta} and {@link HostMetaJson}.
 */
@NonNullByDefault
public abstract sealed class AbstractHostMeta extends ByteStreamRequestResponse implements WellKnownURI.Suffix
        permits HostMeta, HostMetaJson {
    final XRD xrd;

    AbstractHostMeta(final XRD xrd) {
        super(HttpResponseStatus.OK);
        this.xrd = requireNonNull(xrd);
    }

    @Override
    protected final ByteBufResponse toReadyResponse(final ByteBuf content) {
        return new ByteBufResponse(status, content, mediaType());
    }

    protected abstract AsciiString mediaType();

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper).add("xrd", xrd);
    }
}
