/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import com.google.common.collect.AbstractIterator;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.openapi.model.ComponentsEntity;
import org.opendaylight.restconf.openapi.model.OpenApiEntity;
import org.opendaylight.restconf.openapi.model.PathsEntity;
import org.opendaylight.restconf.openapi.model.SchemaEntity;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;

public class OpenApiModelSnippetIterator extends AbstractIterator<Deque<OpenApiEntity>> {
    private final Deque<Module> models = new ArrayDeque<>();
    private final Iterator<? extends Module> schemasIterator;
    private final Iterator<? extends Module> pathsIterator;

    /**
     * We want to have:
     * {
     *     "openapi":
     *     "info"
     *     "servers"
     *     "paths"
     *     "components"
     *     "security"
     * }
     *
     * We can "hardcode" openapi, info, servers and security.
     * But paths and components are made according to EffectiveModelContext.
     */
    private final Deque<ComponentsEntity> components = new ArrayDeque<>();
    private final Deque<PathsEntity> paths = new ArrayDeque<>();

    public OpenApiModelSnippetIterator(final EffectiveModelContext context) {
        schemasIterator = context.getModules().iterator();
        pathsIterator = context.getModules().iterator();
        models.addAll(context.getModules());
    }

    @Override
    protected @NonNull Deque<OpenApiEntity> computeNext() {
        // we are going to iterate over all modules twice
        // 1st time we are returning schemas (components)
        // 2nd time we are returning paths
        if (schemasIterator.hasNext()) {
            return toComponents(schemasIterator.next());
        }
        if (pathsIterator.hasNext()) {
            return toPaths(pathsIterator.next());
        }
        return endOfData();
    }

    private static Deque<OpenApiEntity> toComponents(final Module module) {
        final var result = new ArrayDeque<OpenApiEntity>();
        for (final var rpc : module.getRpcs()) {
            final var entity = new SchemaEntity(rpc.getInput(), "object");
            result.add(entity);
        }

        // actions
        // child nodes
        // etc.
        return result;
    }

    private static Deque<OpenApiEntity> toPaths(final Module module) {
        return new ArrayDeque<>();
    }
}
