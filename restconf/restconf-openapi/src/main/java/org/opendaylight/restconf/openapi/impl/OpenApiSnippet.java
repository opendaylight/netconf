/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import java.util.Iterator;
import org.opendaylight.restconf.openapi.model.OpenApiEntity;

public class OpenApiSnippet implements Iterable<OpenApiEntity> {
    private final OpenApiEntity entity;

    public OpenApiSnippet(final OpenApiEntity entity) {
        this.entity = entity;
    }

    @Override
    public Iterator<OpenApiEntity> iterator() {
        return null;
    }
}
