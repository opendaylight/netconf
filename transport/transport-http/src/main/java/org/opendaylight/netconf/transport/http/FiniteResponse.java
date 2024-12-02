/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import com.google.common.annotations.Beta;
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import io.netty.buffer.ByteBufAllocator;
import java.io.IOException;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A finite {@link Response}, whose formatting needs to be formatted outside of the Netty event loop.
 */
@Beta
@NonNullByDefault
public abstract non-sealed class FiniteResponse implements Response {
    /**
     * Convert this response tp a {@link ReadyResponse}.
     *
     * @param alloc {@link ByteBufAllocator} to use for body
     * @return a {@link ReadyResponse}
     * @throws IOException when an I/O error occurs
     */
    public abstract ReadyResponse toReadyResponse(ByteBufAllocator alloc) throws IOException;

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this).omitNullValues()).toString();
    }

    protected abstract ToStringHelper addToStringAttributes(ToStringHelper helper);
}
