/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import com.google.common.collect.AbstractIterator;
import java.io.IOException;
import java.io.Reader;
import org.eclipse.jdt.annotation.NonNull;

public class OpenApiModelReader extends AbstractIterator<Integer> {
    private final Reader reader;

    public OpenApiModelReader(final Reader reader) {
        this.reader = reader;
    }

    @Override
    protected @NonNull Integer computeNext() {
        try {
            final var read = reader.read();
            if (read == -1) {
                endOfData();
            }
            return read;
        } catch (IOException e) {
            endOfData();
            return -1;
        }
    }
}
