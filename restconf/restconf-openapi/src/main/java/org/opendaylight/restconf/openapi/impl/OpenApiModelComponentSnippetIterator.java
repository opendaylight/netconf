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
import org.opendaylight.restconf.openapi.model.OpenApiEntity;
import org.opendaylight.restconf.openapi.model.SchemaEntity;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;

public class OpenApiModelComponentSnippetIterator extends AbstractIterator<Deque<OpenApiEntity>> {
    private final Iterator<? extends Module> modulesIterator;

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
    public OpenApiModelComponentSnippetIterator(final EffectiveModelContext context) {
        modulesIterator = context.getModules().iterator();
    }

    @Override
    protected @NonNull Deque<OpenApiEntity> computeNext() {
        // we are going to iterate over all modules twice
        // 1st time we are returning schemas (components)
        // 2nd time we are returning paths
        if (modulesIterator.hasNext()) {
            return toComponents(modulesIterator.next());
        }
        return endOfData();
    }

    private static Deque<OpenApiEntity> toComponents(final Module module) {
        final var result = new ArrayDeque<OpenApiEntity>();
        for (final var rpc : module.getRpcs()) {
            final var moduleName = module.getName();
            final var rpcName = rpc.getQName().getLocalName();
            final var input = new SchemaEntity(rpc.getInput(), moduleName + "_" + rpcName + "_input", "object");
            result.add(input);
            final var output = new SchemaEntity(rpc.getOutput(), moduleName + "_" + rpcName + "_output", "object");
            result.add(output);
        }

        // actions
        // child nodes
        // etc.
        return result;
    }
}
