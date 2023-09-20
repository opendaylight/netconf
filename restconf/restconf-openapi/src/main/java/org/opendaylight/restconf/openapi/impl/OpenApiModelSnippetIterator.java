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
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.openapi.model.OpenApiEntity;
import org.opendaylight.restconf.openapi.model.SchemaEntity;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;

public class OpenApiModelSnippetIterator extends AbstractIterator<Deque<OpenApiEntity>> {
    private final Deque<Module> models = new ArrayDeque<>();

    public OpenApiModelSnippetIterator(final EffectiveModelContext context) {
        models.addAll(context.getModules());
    }

    @Override
    protected @NonNull Deque<OpenApiEntity> computeNext() {
        if (models.isEmpty()) {
            return endOfData();
        }
        return toNextDeck(models.pop());
    }

    private static Deque<OpenApiEntity> toNextDeck(final Module module) {
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
}
