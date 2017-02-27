/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.schema.mapping;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import org.opendaylight.yangtools.sal.binding.generator.impl.ModuleInfoBackedContext;
import org.opendaylight.yangtools.yang.binding.YangModuleInfo;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public enum BaseSchema {

    BASE_NETCONF_CTX(
            Lists.newArrayList(
                    org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.$YangModuleInfoImpl.getInstance(),
                    org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.extension.rev131210.$YangModuleInfoImpl.getInstance()
            )
    );

    private final Map<QName, RpcDefinition> mappedRpcs;
    private final SchemaContext schemaContext;

    BaseSchema(final List<YangModuleInfo> modules) {
        try {
            final ModuleInfoBackedContext moduleInfoBackedContext = ModuleInfoBackedContext.create();
            moduleInfoBackedContext.addModuleInfos(modules);
            schemaContext = moduleInfoBackedContext.tryToCreateSchemaContext().get();
            mappedRpcs = Maps.uniqueIndex(schemaContext.getOperations(), RpcDefinition::getQName);
        } catch (final RuntimeException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    Map<QName, RpcDefinition> getMappedRpcs() {
        return mappedRpcs;
    }

    public SchemaContext getSchemaContext() {
        return schemaContext;
    }

}
