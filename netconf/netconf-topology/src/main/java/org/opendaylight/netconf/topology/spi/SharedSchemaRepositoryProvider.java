/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.topology.api.SchemaRepositoryProvider;
import org.opendaylight.yangtools.yang.model.parser.api.YangParserFactory;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;

public class SharedSchemaRepositoryProvider implements SchemaRepositoryProvider {
    private final @NonNull SharedSchemaRepository schemaRepository;

    public SharedSchemaRepositoryProvider(final String moduleName, final YangParserFactory parserFactory) {
        schemaRepository = new SharedSchemaRepository(moduleName, parserFactory);
    }

    @Override
    public SharedSchemaRepository getSharedSchemaRepository() {
        return schemaRepository;
    }
}
