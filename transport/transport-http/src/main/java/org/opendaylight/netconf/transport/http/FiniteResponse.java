/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import java.io.IOException;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A finite {@link Response}, whose formatting needs to be performed outside of the Netty event loop. The HTTP framework
 * will arrange for {@link #writeTo(ResponseOutput)} to be executed on a separate thread.
 */
@NonNullByDefault
public abstract non-sealed class FiniteResponse implements Response {
    @Override
    public FiniteResponse asResponse() {
        return this;
    }

    /**
     * Write this response into the provided {@link ResponseOutput}.
     *
     * @param output the {@link ResponseOutput}
     * @throws IOException when an I/O error occurs
     */
    public abstract void writeTo(ResponseOutput output) throws IOException;

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this).omitNullValues()).toString();
    }

    protected abstract ToStringHelper addToStringAttributes(ToStringHelper helper);
}
