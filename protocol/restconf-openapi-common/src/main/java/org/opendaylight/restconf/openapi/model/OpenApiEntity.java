/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import org.eclipse.jdt.annotation.NonNull;

/**
 * A response entity for complex generated type.
 */
public abstract sealed class OpenApiEntity permits ComponentsEntity, DocumentEntity, InfoEntity, MetadataEntity,
        MountPointsEntity, OpenApiVersionEntity, OperationEntity, PathEntity, PathsEntity, SchemaEntity, SchemasEntity,
        SecurityEntity, SecuritySchemesEntity, ServerEntity, ServersEntity {
    /**
     * Generate JSON events into specified generator.
     *
     * @param generator JsonGenerator to emit events to
     * @throws IOException when an error occurs
     */
    public abstract void generate(@NonNull JsonGenerator generator) throws IOException;
}
