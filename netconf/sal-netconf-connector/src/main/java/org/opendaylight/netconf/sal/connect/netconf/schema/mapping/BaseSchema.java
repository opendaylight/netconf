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
import org.opendaylight.mdsal.binding.generator.impl.ModuleInfoBackedContext;
import org.opendaylight.yangtools.yang.binding.YangModuleInfo;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContextProvider;

public enum BaseSchema implements SchemaContextProvider {
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

    private final ImmutableMap<QName, RpcDefinition> mappedRpcs;
    private final SchemaContext schemaContext;

    BaseSchema(final YangModuleInfo... modules) {
        final ModuleInfoBackedContext moduleInfoBackedContext = ModuleInfoBackedContext.create();
        moduleInfoBackedContext.addModuleInfos(Arrays.asList(modules));
        schemaContext = moduleInfoBackedContext.tryToCreateSchemaContext().get();
        mappedRpcs = Maps.uniqueIndex(schemaContext.getOperations(), RpcDefinition::getQName);
    }

    ImmutableMap<QName, RpcDefinition> getMappedRpcs() {
        return mappedRpcs;
    }

    @Override
    public SchemaContext getSchemaContext() {
        return schemaContext;
    }
}
