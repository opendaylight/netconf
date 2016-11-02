/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.impl;

import org.opendaylight.netconf.topology.api.SchemaRepositoryProvider;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;

public class SchemaRepositoryProviderImpl implements SchemaRepositoryProvider {

    private final SharedSchemaRepository schemaRepository;

    public SchemaRepositoryProviderImpl(final String moduleName) {
        schemaRepository = new SharedSchemaRepository(moduleName);
    }

    @Override
    public SharedSchemaRepository getSharedSchemaRepository() {
        return schemaRepository;
    }
}