/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.config.yang.netconf.mdsal.yang.library;

import org.opendaylight.netconf.mdsal.yang.library.SchemaServiceToMdsalWriter;

public class NetconfMdsalYanglibModule extends org.opendaylight.controller.config.yang.netconf.mdsal.yang.library.AbstractNetconfMdsalYanglibModule {
    public NetconfMdsalYanglibModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public NetconfMdsalYanglibModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.controller.config.yang.netconf.mdsal.yang.library.NetconfMdsalYanglibModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        // TODO Implement also yang-library-change notfication
        final SchemaServiceToMdsalWriter schemaServiceToMdsalWriter =
                new SchemaServiceToMdsalWriter(getRootSchemaServiceDependency());

        getBindingAwareBrokerDependency().registerProvider(schemaServiceToMdsalWriter);

        return schemaServiceToMdsalWriter;
    }

}
