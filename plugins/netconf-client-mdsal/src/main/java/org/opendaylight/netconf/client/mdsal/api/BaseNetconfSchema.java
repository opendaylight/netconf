/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import java.util.Map;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;

/**
 * A {@link MountPointContext} corresponding to a NETCONF baseline advertized by a device..
 */
@NonNullByDefault
public interface BaseNetconfSchema extends Immutable {
    /**
     * Return the {@link MountPointContext}.
     *
     * @return the mount point context
     */
    MountPointContext mountPointContext();

    /**
     * Return the {@link MountPointContext}. This is a convenience equivalent to
     * {@code mountPointContext().modelContext()}.
     *
     * @return the mount point context
     */
    default EffectiveModelContext modelContext() {
        return mountPointContext().modelContext();
    }

    /**
     * Return the set of RPCs available in {@link #modelContext()}.
     *
     * @return the set of available RPCs
     */
    Map<QName, ? extends RpcDefinition> mappedRpcs();
}
