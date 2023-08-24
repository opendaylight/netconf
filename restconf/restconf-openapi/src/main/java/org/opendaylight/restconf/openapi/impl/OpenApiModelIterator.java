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
import org.opendaylight.yangtools.yang.model.api.Module;

public class OpenApiModelIterator extends AbstractIterator<Integer> {
    private final Module module;
    private final Deque<OpenApiEntity> entities = new ArrayDeque<>();

    public OpenApiModelIterator(final Module module) {
        this.module = module;
    }

    @Override
    protected @NonNull Integer computeNext() {
        for (final var identity : module.getIdentities()) {
            final var entity = new SchemaEntity(identity, "string"); //?
        }

        return null;
    }
}
