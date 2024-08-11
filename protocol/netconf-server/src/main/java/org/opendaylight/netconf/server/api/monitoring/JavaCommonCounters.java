/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.api.monitoring;

import com.google.common.annotations.Beta;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.CommonCounters;

/**
 * Java-native implementation {@link CommonCounters}. Exposes each counter as a long which is guaranteed to be a valid
 * {@code uint32} value.
 */
@Beta
@NonNullByDefault
public interface JavaCommonCounters extends CommonCounters {
    /**
     * Returns {@link #getInRpcs()} as a long value.
     *
     * @return {@link #getInRpcs()} as a long value
     */
    long inRpcs();

    /**
     * Returns {@link #getInBadRpcs()} as a long value.
     *
     * @return {@link #getInBadRpcs()} as a long value
     */
    long inBadRpcs();

    /**
     * Returns {@link #getOutRpcErrors()} as a long value.
     *
     * @return {@link #getOutRpcErrors()} as a long value
     */
    long outRpcErrors();

    /**
     * Returns {@link #getOutNotifications()} as a long value.
     *
     * @return {@link #getOutNotifications()} as a long value
     */
    long outNotifications();

    @Override
    default Class<CommonCounters> implementedInterface() {
        return CommonCounters.class;
    }
}
