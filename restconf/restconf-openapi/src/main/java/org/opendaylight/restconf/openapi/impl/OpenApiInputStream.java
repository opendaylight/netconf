/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import org.opendaylight.restconf.openapi.jaxrs.OpenApiBodyWriter;
import org.opendaylight.restconf.openapi.model.Info;
import org.opendaylight.restconf.openapi.model.InfoEntity;
import org.opendaylight.restconf.openapi.model.OpenApiEntity;
import org.opendaylight.restconf.openapi.model.Server;
import org.opendaylight.restconf.openapi.model.ServerEntity;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public class OpenApiInputStream extends InputStream {
    private final ArrayDeque<OpenApiEntity> stack = new ArrayDeque<>();

    private Reader reader;

    public OpenApiInputStream(final EffectiveModelContext context, final String openApiVersion, final Info info,
            final List<Server> servers, final List<Map<String, List<String>>> security) {
        stack.add(new InfoEntity(info.version(), info.title(), info.description()));
        stack.add(new ServerEntity(servers.iterator().next().url()));
    }

    @Override
    public int read() throws IOException {
        if (reader == null) {
            reader = new InputStreamReader(new ByteArrayInputStream(writeNextEntity(stack.pop())));
        }

        var read = reader.read();
        while (read == -1) {
            if (stack.isEmpty()) {
                return -1;
            }
            reader = new InputStreamReader(new ByteArrayInputStream(writeNextEntity(stack.pop())));
            read = reader.read();
        }

        return read;
    }

    private static byte[] writeNextEntity(final OpenApiEntity entity) throws IOException {
        var openApiBodyWriter = new OpenApiBodyWriter();
        var outStream = new ByteArrayOutputStream();
        openApiBodyWriter.writeTo(entity, null, null, null, null, null, outStream);
        return outStream.toByteArray();
    }
}
