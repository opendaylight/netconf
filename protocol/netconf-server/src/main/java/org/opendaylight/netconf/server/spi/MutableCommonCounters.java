/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.spi;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.server.api.monitoring.MutableJavaCommonCounters;
import org.opendaylight.netconf.server.api.monitoring.JavaCommonCounters;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.CommonCounters;

/**
 * Default implementation of {@link MutableJavaCommonCounters}.
 */
@NonNullByDefault
public final class MutableCommonCounters extends AbstractJavaCommonCounters implements MutableJavaCommonCounters {
    private int inRpcs;
    private int inBadRpcs;
    private int outRpcErrors;
    private int outNotifications;

    private MutableCommonCounters() {
        // for default initialization
    }

    private MutableCommonCounters(final int inRpcs, final int inBadRpcs, final int outRpcErrors,
            final int outNotifications) {
        this.inRpcs = inRpcs;
        this.inBadRpcs = inBadRpcs;
        this.outRpcErrors = outRpcErrors;
        this.outNotifications = outNotifications;
    }

    MutableCommonCounters(final AbstractJavaCommonCounters from) {
        inRpcs = from.rawInRpcs();
        inBadRpcs = from.rawInBadRpcs();
        outRpcErrors = from.rawOutRpcErrors();
        outNotifications = from.rawOutNotifications();
    }

    /**
     * Return a new instance with all counters are set to zero.
     */
    public static MutableCommonCounters of() {
        return new MutableCommonCounters();
    }

    /**
     * Return a new instance with all counters initialized from another {@link CommonCounters}.
     *
     * @param values initial counter values
     */
    public static MutableCommonCounters copyOf(final CommonCounters values) {
        return switch (values) {
            case AbstractJavaCommonCounters from -> new MutableCommonCounters(from);
            case JavaCommonCounters from -> new MutableCommonCounters(raw(from.inRpcs()), raw(from.inBadRpcs()),
                raw(from.outRpcErrors()), raw(from.outNotifications()));
            default -> new MutableCommonCounters(raw(values.requireInRpcs()), raw(values.requireInBadRpcs()),
                raw(values.requireOutRpcErrors()), raw(values.requireOutNotifications()));
        };
    }

    @Override
    public void incInRpcs() {
        inRpcs++;
    }

    @Override
    public void incInBadRpcs() {
        inBadRpcs++;
    }

    @Override
    public void incOutRpcErrors() {
        outRpcErrors++;
    }

    @Override
    public void incOutNotifications() {
        outNotifications++;
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
