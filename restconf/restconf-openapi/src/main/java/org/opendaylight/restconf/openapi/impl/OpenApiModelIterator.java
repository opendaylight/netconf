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
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.openapi.jaxrs.OpenApiBodyWriter;
import org.opendaylight.restconf.openapi.model.Info;
import org.opendaylight.restconf.openapi.model.InfoEntity;
import org.opendaylight.restconf.openapi.model.OpenApiEntity;
import org.opendaylight.restconf.openapi.model.Server;
import org.opendaylight.restconf.openapi.model.ServerEntity;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public class OpenApiModelIterator extends AbstractIterator<Integer> {
    private static final ArrayDeque<OpenApiEntity> stack = new ArrayDeque<>();

    public OpenApiModelIterator(final EffectiveModelContext context, final String openApiVersion, final Info info,
        final List<Server> servers, final List<Map<String, List<String>>> security) {
        stack.add(new InfoEntity(info.version(), info.title(), info.description()));
        stack.add(new ServerEntity(servers.iterator().next().url()));
    }

    @Override
    protected @NonNull Integer computeNext() {
        if (stack.isEmpty()) {
            endOfData();
            return -1;
        }

        try {
            final var pop = stack.pop();
            final var openApiBodyWriter = new OpenApiBodyWriter();
            final var outStream = new ByteArrayOutputStream();
            openApiBodyWriter.writeTo(pop, null, null, null, null, null, outStream);

            final var reader = new InputStreamReader(new ByteArrayInputStream(outStream.toByteArray()));
            final var read = reader.read();
            while (read != -1) { // FIXME
                endOfData();
            }
            return read;
        } catch (IOException e) {
            endOfData();
            return -1;
        }
    }
}
