/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import java.util.Deque;
import java.util.Iterator;
import org.opendaylight.restconf.openapi.model.OpenApiEntity;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public class OpenApiModelComponentSnippet implements Iterable<Deque<OpenApiEntity>> {
    private final EffectiveModelContext context;

    public OpenApiModelComponentSnippet(final EffectiveModelContext context) {
        this.context = context;
    }

    @Override
    public Iterator<Deque<OpenApiEntity>> iterator() {
        return new OpenApiModelComponentSnippetIterator(context);
    }
}
