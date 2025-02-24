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
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchema;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;

/**
 * An {@link DatabindContext} corresponding to a NETCONF baseline advertized by a device.
 */
public final class DefaultBaseNetconfSchema implements BaseNetconfSchema {
    private final @NonNull ImmutableMap<QName, ? extends RpcDefinition> mappedRpcs;
    private final @NonNull DatabindContext databind;

    DefaultBaseNetconfSchema(final EffectiveModelContext modelContext) {
        mappedRpcs = Maps.uniqueIndex(modelContext.getOperations(), RpcDefinition::getQName);
        databind = DatabindContext.ofModel(modelContext);
    }

    @Override
    public DatabindContext databind() {
        return databind;
    }

    @Override
    public ImmutableMap<QName, ? extends RpcDefinition> mappedRpcs() {
        return mappedRpcs;
    }
}
