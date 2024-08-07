/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.openapi.jaxrs.OpenApiBodyWriter;
import org.opendaylight.restconf.openapi.model.MetadataEntity;
import org.opendaylight.restconf.openapi.model.OpenApiEntity;

public final class MetadataStream extends InputStream {
    private static final String TOTAL_MODULES = "totalModules";
    private static final String CONFIG_MODULES = "configModules";
    private static final String NON_CONFIG_MODULES = "nonConfigModules";
    private static final String CURRENT_PAGE = "currentPage";
    private static final String TOTAL_PAGES = "totalPages";

    private final ByteArrayOutputStream stream = new ByteArrayOutputStream();
    private final JsonGenerator generator = new JsonFactoryBuilder().build().createGenerator(stream);
    private final OpenApiBodyWriter writer;
    private final int offset;
    private final int limit;
    private final long configModules;
    private final long allModules;

    private Reader reader;
    private ReadableByteChannel channel;

    public MetadataStream(final int offset, final int limit, final long allModules, final long configModules)
            throws IOException {
        this.writer = new OpenApiBodyWriter(generator, stream);
        this.offset = offset;
        this.limit = limit;
        this.configModules = configModules;
        this.allModules = allModules;
    }

    @Override
    public int read() throws IOException {
        if (reader == null) {
            reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(
                writeNextEntity(new MetadataEntity(mappedMetadata()))), StandardCharsets.UTF_8));
        }
        return reader.read();
    }

    @Override
    public int read(final byte[] array, final int off, final int len) throws IOException {
        if (channel == null) {
            channel = Channels.newChannel(new ByteArrayInputStream(
                writeNextEntity(new MetadataEntity(mappedMetadata()))));
        }
        return channel.read(ByteBuffer.wrap(array, off, len));
    }

    private byte[] writeNextEntity(final OpenApiEntity entity) throws IOException {
        writer.writeTo(entity, null, null, null, null, null, null);
        return writer.readFrom();
    }

    private Map<String, ?> mappedMetadata() {
        if (limit == 0 && offset == 0) {
            return metadataForAllModules(configModules, allModules);
        } else {
            return metadataForPagedModules(offset, limit, configModules, allModules);
        }
    }

    private static Map<String, ?> metadataForAllModules(final long configModules, final long allModules) {
        return Map.of(
            TOTAL_MODULES, allModules,
            CONFIG_MODULES, configModules,
            NON_CONFIG_MODULES, allModules - configModules,
            CURRENT_PAGE, 1,
            TOTAL_PAGES, 1
        );
    }

    private static Map<String, ?> metadataForPagedModules(final int offset, final int limit, final long configModules,
            final long allModules) {
        return Map.of(
            TOTAL_MODULES, allModules,
            CONFIG_MODULES, configModules,
            NON_CONFIG_MODULES, allModules - configModules,
            "limit", limit,
            "offset", offset,
            CURRENT_PAGE, offset / limit + 1,
            TOTAL_PAGES, configModules / limit + 1,
            "previousOffset", Math.max(offset - limit, 0),
            "nextOffset", Math.min(offset + limit, configModules)
        );
    }
}
