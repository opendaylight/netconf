/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.config.yang.netconf.topology.shared.schema.repository;

import org.opendaylight.netconf.topology.SchemaRepositoryProvider;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;

public class SchemaRepositoryImplModule extends org.opendaylight.controller.config.yang.netconf.topology.shared.schema.repository.AbstractSchemaRepositoryImplModule {
    public SchemaRepositoryImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public SchemaRepositoryImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.controller.config.yang.netconf.topology.shared.schema.repository.SchemaRepositoryImplModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        return new SchemaRepositoryProviderAutoCloseAble(this);
    }

    private static class SchemaRepositoryProviderAutoCloseAble implements SchemaRepositoryProvider, AutoCloseable {

        private final SharedSchemaRepository schemaRepository;

        public SchemaRepositoryProviderAutoCloseAble(SchemaRepositoryImplModule module) {
            schemaRepository = new SharedSchemaRepository(module.getIdentifier().getInstanceName());
        }

        @Override
        public void close() throws Exception {
            //NOOP
        }

        @Override
        public SharedSchemaRepository getSharedSchemaRepository() {
            return schemaRepository;
        }
    }
}
