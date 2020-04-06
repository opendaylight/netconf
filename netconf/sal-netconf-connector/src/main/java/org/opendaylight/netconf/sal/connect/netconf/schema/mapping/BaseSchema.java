/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.schema.mapping;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.Arrays;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.yangtools.rcf8528.data.util.EmptyMountPointContext;
import org.opendaylight.yangtools.yang.binding.YangModuleInfo;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContextProvider;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;

public enum BaseSchema implements EffectiveModelContextProvider {
    BASE_NETCONF_CTX(
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601
            .$YangModuleInfoImpl.getInstance(),
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.extension.rev131210
            .$YangModuleInfoImpl.getInstance()
    ),
    BASE_NETCONF_CTX_WITH_NOTIFICATIONS(
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.extension.rev131210
            .$YangModuleInfoImpl.getInstance(),
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714
            .$YangModuleInfoImpl.getInstance(),
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601
            .$YangModuleInfoImpl.getInstance(),
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206
            .$YangModuleInfoImpl.getInstance()
    );

    private final @NonNull ImmutableMap<QName, ? extends RpcDefinition> mappedRpcs;
    private final @NonNull EmptyMountPointContext mountContext;

    BaseSchema(final YangModuleInfo... modules) {
        mountContext = new EmptyMountPointContext(BindingRuntimeHelpers.createEffectiveModel(Arrays.asList(modules)));
        mappedRpcs = Maps.uniqueIndex(getSchemaContext().getOperations(), RpcDefinition::getQName);
    }

    @NonNull ImmutableMap<QName, ? extends RpcDefinition> getMappedRpcs() {
        return mappedRpcs;
    }

    public @NonNull EmptyMountPointContext getMountPointContext() {
        return mountContext;
    }

    @Override
    public @NonNull EffectiveModelContext getEffectiveModelContext() {
        return mountContext.getEffectiveModelContext();
    }
}
