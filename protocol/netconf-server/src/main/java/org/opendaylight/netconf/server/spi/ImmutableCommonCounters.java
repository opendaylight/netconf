/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.spi;

import static com.google.common.primitives.UnsignedInts.checkedCast;
import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.server.api.monitoring.JavaCommonCounters;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.CommonCounters;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * {@link Immutable} implementation of {@link JavaCommonCounters}.
 */
@NonNullByDefault
public final class ImmutableCommonCounters extends AbstractJavaCommonCounters implements Immutable {
    private final int inRpcs;
    private final int inBadRpcs;
    private final int outRpcErrors;
    private final int outNotifications;

    private ImmutableCommonCounters(final int inRpcs, final int inBadRpcs, final int outRpcErrors,
            final int outNotifications) {
        this.inRpcs = inRpcs;
        this.inBadRpcs = inBadRpcs;
        this.outRpcErrors = outRpcErrors;
        this.outNotifications = outNotifications;
    }

    private ImmutableCommonCounters(final CommonCounters from) {
        this(unwrap(from.requireInRpcs()), unwrap(from.requireInBadRpcs()), unwrap(from.requireOutRpcErrors()),
            unwrap(from.requireOutNotifications()));
    }

    public static ImmutableCommonCounters copyOf(final CommonCounters values) {
        return switch (values) {
            case ImmutableCommonCounters immutable -> immutable;
            case MutableCommonCounters from -> copyOf(from);
            case JavaCommonCounters from -> new ImmutableCommonCounters(checkedCast(from.inRpcs()),
                checkedCast(from.inBadRpcs()), checkedCast(from.outRpcErrors()), checkedCast(from.outNotifications()));
            default -> new ImmutableCommonCounters(values);
        };
    }

    public static ImmutableCommonCounters copyOf(final MutableCommonCounters values) {
        return new ImmutableCommonCounters(values.rawInRpcs(), values.rawInBadRpcs(), values.rawOutRpcErrors(),
            values.rawOutNotifications());
    }

    // Do not call
    @Deprecated(forRemoval = true, since = "8.0.1")
    public static ImmutableCommonCounters copyOf(final ImmutableCommonCounters counters) {
        return requireNonNull(counters);
    }

    public MutableCommonCounters toMutable() {
        return new MutableCommonCounters(this);
    }

    @Override
    int rawInRpcs() {
        return inRpcs;
    }

    @Override
    int rawInBadRpcs() {
        return inBadRpcs;
    }

    @Override
    int rawOutRpcErrors() {
        return outRpcErrors;
    }

    @Override
    int rawOutNotifications() {
        return outNotifications;
    }
}
