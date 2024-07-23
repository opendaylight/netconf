/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;

/**
 * Configuration common to all RESTCONF server endpoints.
 */
public abstract class EndpointConfiguration {
    // FIXME: can this be 64KiB exactly? if so, maximumFragmentLength should become a Uint16 and validation should be
    //        pushed out to users
    public static final int SSE_MAXIMUM_FRAGMENT_LENGTH_MAX = 65534;

    private final @NonNull ErrorTagMapping errorTagMapping;
    private final @NonNull PrettyPrintParam prettyPrint;
    private final @NonNull Uint16 sseMaximumFragmentLength;
    private final @NonNull Uint32 sseHeartbeatIntervalMillis;

    protected EndpointConfiguration(final ErrorTagMapping errorTagMapping, final PrettyPrintParam prettyPrint,
            final Uint16 sseMaximumFragmentLength, final Uint32 sseHeartbeatIntervalMillis) {
        this.errorTagMapping = requireNonNull(errorTagMapping);
        this.prettyPrint = requireNonNull(prettyPrint);
        this.sseMaximumFragmentLength = requireNonNull(sseMaximumFragmentLength);
        this.sseHeartbeatIntervalMillis = requireNonNull(sseHeartbeatIntervalMillis);

        final var fragSize = sseMaximumFragmentLength.toJava();
        if (fragSize != 0 && fragSize < SSE_MAXIMUM_FRAGMENT_LENGTH_MAX) {
            throw new IllegalArgumentException(
                "Maximum fragment length must be disabled (0) or specified by positive value less than 64KiB");
        }
    }

    public final ErrorTagMapping errorTagMapping() {
        return errorTagMapping;
    }

    public final PrettyPrintParam prettyPrint() {
        return prettyPrint;
    }

    /**
     * Maximum fragment length in number of Unicode code units (characters) of a Server-Sent Event. Events exceeding
     * this limit will be fragmented into multiple messages. {@link Uint32#ZERO} if no limit exists.
     *
     * @return maximum fragment length, in Unicode code units
     */
    public final Uint16 sseMaximumFragmentLength() {
        return sseMaximumFragmentLength;
    }

    /**
     * Server-Sent Events heartbeat interval, in milliseconds. {@link Uint32#ZERO} if no heartbeats should be sent.
     *
     * @return heartbeat interval, in milliseconds
     */
    public final Uint32 sseHeartbeatIntervalMillis() {
        return sseHeartbeatIntervalMillis;
    }

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this)).toString();
    }

    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper
            .add("errorTagMapping", errorTagMapping)
            .add("prettyPrint", prettyPrint)
            .add("sseMaximumFragmentLength", sseMaximumFragmentLength.toJava())
            .add("sseHeartbeatIntervalMillis", sseHeartbeatIntervalMillis.toJava());
    }
}
