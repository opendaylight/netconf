/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.api;

<<<<<<< HEAD   (14a6d8 Bump versions to 5.0.9-SNAPSHOT)
=======
import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;
>>>>>>> CHANGE (820f7a Throw exception when module cannot be found)
import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;

<<<<<<< HEAD   (14a6d8 Bump versions to 5.0.9-SNAPSHOT)
public final class SchemaExportContext {
    private final EffectiveModelContext schemaContext;
    private final Module module;
    private final DOMYangTextSourceProvider sourceProvider;
=======
/**
 * Holder of schema export context.
 */
public record SchemaExportContext(
    @NonNull EffectiveModelContext schemaContext,
    @NonNull Module module,
    @NonNull DOMYangTextSourceProvider sourceProvider) {
>>>>>>> CHANGE (820f7a Throw exception when module cannot be found)

<<<<<<< HEAD   (14a6d8 Bump versions to 5.0.9-SNAPSHOT)
    public SchemaExportContext(final EffectiveModelContext schemaContext, final Module module,
                               final DOMYangTextSourceProvider sourceProvider) {
        this.schemaContext = schemaContext;
        this.module = module;
        this.sourceProvider = sourceProvider;
    }

    public EffectiveModelContext getSchemaContext() {
        return schemaContext;
    }

    public Module getModule() {
        return module;
    }

    public DOMYangTextSourceProvider getSourceProvider() {
        return sourceProvider;
=======
    public SchemaExportContext {
        requireNonNull(schemaContext);
        requireNonNull(module);
        requireNonNull(sourceProvider);
>>>>>>> CHANGE (820f7a Throw exception when module cannot be found)
    }
}
