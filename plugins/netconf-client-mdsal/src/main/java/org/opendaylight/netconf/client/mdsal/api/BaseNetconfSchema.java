/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import com.google.common.collect.ImmutableMap;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;

/**
 * A {@link MountPointContext} corresponding to a NETCONF baseline advertized by a device. This baseline is sufficient
 * to invoke basic NETCONF operations and perform schema discovery.
 */
@NonNullByDefault
public interface BaseNetconfSchema extends Immutable {
    /**
     * Return the {@link DatabindContext}.
     *
     * @return the databind
     */
    DatabindContext databind();

    /**
     * Return the {@link MountPointContext}. This is a convenience equivalent to {@code databind().mountContext()}.
     *
     * @return the mount point context
     */
    @Deprecated(since = "9.0.0")
    default MountPointContext mountPointContext() {
        return databind().mountContext();
    }

    /**
     * Return the {@link EffectiveModelContext}. This is a convenience equivalent to {@code databind().modelContext()}.
     *
     * @return the model context
     */
    @Deprecated(since = "9.0.0")
    default EffectiveModelContext modelContext() {
        return databind().modelContext();
    }

    /**
     * Return the set of RPCs available in {@link #modelContext()}.
     *
     * @return the set of available RPCs
     */
    ImmutableMap<QName, ? extends RpcDefinition> mappedRpcs();
}
