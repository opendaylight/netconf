/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import com.google.common.collect.AbstractIterator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.Deque;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.openapi.jaxrs.OpenApiBodyWriter;
import org.opendaylight.restconf.openapi.model.OpenApiEntity;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public class OpenApiModelIterator extends AbstractIterator<Integer> {
    private static final byte[] ARRAY = new byte[0];
    private static final Reader READER = new InputStreamReader(new ByteArrayInputStream(ARRAY));

    public OpenApiModelIterator(final OpenApiEntity entity) throws IOException { // FIXME
        final var openApiWriter = new OpenApiBodyWriter();
        final var outStream = new ByteArrayOutputStream(0);
        openApiWriter.writeTo(entity, null, null, null, null, null, outStream);
    }

    @Override
    protected @NonNull Integer computeNext() {
        try {
            final var read = READER.read();
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
