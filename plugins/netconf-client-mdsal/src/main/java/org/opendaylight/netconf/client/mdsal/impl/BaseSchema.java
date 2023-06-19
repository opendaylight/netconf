/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContextProvider;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;

public final class BaseSchema implements EffectiveModelContextProvider, Immutable {
    private final @NonNull ImmutableMap<QName, ? extends RpcDefinition> mappedRpcs;
    private final @NonNull MountPointContext mountContext;

    BaseSchema(final EffectiveModelContext context) {
        mountContext = MountPointContext.of(context);
        mappedRpcs = Maps.uniqueIndex(context.getOperations(), RpcDefinition::getQName);
    }

    @NonNull ImmutableMap<QName, ? extends RpcDefinition> getMappedRpcs() {
        return mappedRpcs;
    }

    public @NonNull MountPointContext getMountPointContext() {
        return mountContext;
    }

    @Override
    public @NonNull EffectiveModelContext getEffectiveModelContext() {
        return mountContext.getEffectiveModelContext();
    }
}
