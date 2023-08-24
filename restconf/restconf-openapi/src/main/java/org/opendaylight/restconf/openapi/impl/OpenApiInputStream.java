/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import org.opendaylight.yangtools.yang.model.api.Module;

public class OpenApiInputStream extends InputStream implements Iterable<Integer> {
    private final Module module;

    public OpenApiInputStream(final Module module) {
        this.module = module;
    }

    @Override
    public Iterator<Integer> iterator() {
        return new OpenApiModelIterator(module);
    }

    @Override
    public int read() throws IOException {
        return iterator().next();
    }
}
