/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.legacy;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.ModuleEffectiveStatement;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;

/**
 * Holder of schema export context.
 */
@NonNullByDefault
public record SchemaExportContext(
    EffectiveModelContext schemaContext,
    ModuleEffectiveStatement module,
    SchemaSourceProvider<YangTextSchemaSource> sourceProvider) {

    public SchemaExportContext {
        requireNonNull(schemaContext);
        requireNonNull(module);
        requireNonNull(sourceProvider);
    }
}
