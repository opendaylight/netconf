/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http.rfc6415;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects.ToStringHelper;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AsciiString;
import java.io.IOException;
import java.io.OutputStream;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.AbstractFiniteResponse;
import org.opendaylight.netconf.transport.http.ResponseOutput;
import org.opendaylight.netconf.transport.http.WellKnownURI;

/**
 * Abstract based class for {@link HostMeta} and {@link HostMetaJson}.
 */
@NonNullByDefault
public abstract sealed class AbstractHostMeta extends AbstractFiniteResponse implements WellKnownURI.Suffix
        permits HostMeta, HostMetaJson {
    final XRD xrd;

    AbstractHostMeta(final XRD xrd) {
        super(HttpResponseStatus.OK);
        this.xrd = requireNonNull(xrd);
    }

    @Override
    public final void writeTo(final ResponseOutput output) throws IOException {
        try (var out = output.start(status(), HttpHeaderNames.CONTENT_TYPE, mediaType())) {
            try {
                writeBody(out);
            } catch (IOException e) {
                out.handleError(e);
                throw e;
            }
        }
    }

    @VisibleForTesting
    public abstract AsciiString mediaType();

    protected abstract void writeBody(OutputStream out) throws IOException;

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper).add("xrd", xrd);
    }
}
