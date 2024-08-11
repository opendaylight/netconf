/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.spi;

import com.google.common.base.MoreObjects;
import org.opendaylight.netconf.server.api.monitoring.JavaCommonCounters;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.ZeroBasedCounter32;
import org.opendaylight.yangtools.yang.common.Uint32;

/**
 * Abstract base class for {@link JavaCommonCounters} implementations.
 */
abstract sealed class AbstractJavaCommonCounters implements JavaCommonCounters
        permits ImmutableCommonCounters, MutableCommonCounters {
    @Override
    public final long inRpcs() {
        return Integer.toUnsignedLong(rawInRpcs());
    }

    @Override
    public final long inBadRpcs() {
        return Integer.toUnsignedLong(rawInBadRpcs());
    }

    @Override
    public final long outRpcErrors() {
        return Integer.toUnsignedLong(rawOutRpcErrors());
    }

    @Override
    public final long outNotifications() {
        return Integer.toUnsignedLong(rawOutNotifications());
    }

    @Override
    public final ZeroBasedCounter32 getInRpcs() {
        return counterOf(rawInRpcs());
    }

    @Override
    public final ZeroBasedCounter32 getInBadRpcs() {
        return counterOf(rawInBadRpcs());
    }

    @Override
    public final ZeroBasedCounter32 getOutRpcErrors() {
        return counterOf(rawOutRpcErrors());
    }

    @Override
    public final ZeroBasedCounter32 getOutNotifications() {
        return counterOf(rawOutNotifications());
    }

    abstract int rawInRpcs();

    abstract int rawInBadRpcs();

    abstract int rawOutRpcErrors();

    abstract int rawOutNotifications();

    @Override
    public final String toString() {
        return MoreObjects.toStringHelper(this)
            .add("in-rpcs", inRpcs())
            .add("in-bad-rpcs", inBadRpcs())
            .add("out-rpc-errors", outRpcErrors())
            .add("out-notifications", outNotifications())
            .toString();
    }

    static final int raw(final long unsigned) {
        if (unsigned >> Integer.SIZE != 0) {
            throw new IllegalArgumentException("Bad counter value " + unsigned);
        }
        return (int) unsigned;
    }

    static final int raw(final ZeroBasedCounter32 counter) {
        return counter.getValue().intValue();
    }

    private static ZeroBasedCounter32 counterOf(final int raw) {
        return new ZeroBasedCounter32(Uint32.fromIntBits(raw));
    }
}
