/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import org.eclipse.jdt.annotation.NonNull;

public final class MetadataEntity extends OpenApiEntity {
    private final int offset;
    private final int limit;
    private final long allModules;
    private final long configModules;

    public MetadataEntity(final int offset, final int limit, final long allModules, final long configModules)
            throws IOException {
        this.offset = offset;
        this.limit = limit;
        this.allModules = allModules;
        this.configModules = configModules;
        if (offset > limit) {
            throw new IOException("Offset " + offset + " is greater than limit " + limit);
        }
    }

    @Override
    public void generate(final @NonNull JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        generator.writeObjectFieldStart("metadata");
        generator.writeStringField("totalModules", String.valueOf(allModules));
        generator.writeStringField("configModules", String.valueOf(configModules));
        generator.writeStringField("nonConfigModules", String.valueOf(allModules - configModules));

        final long currentPage;
        final long totalPages;
        if (limit != 0 || offset != 0) {
            generator.writeStringField("limit", String.valueOf(limit));
            generator.writeStringField("offset", String.valueOf(offset));
            generator.writeStringField("previousOffset", String.valueOf(Math.max(offset - limit, 0)));
            generator.writeStringField("nextOffset", String.valueOf(Math.min(offset + limit, configModules)));
            currentPage = offset / limit + 1;
            totalPages = configModules / limit + 1;
        } else {
            currentPage = 1;
            totalPages = 1;
        }
        generator.writeStringField("currentPage", String.valueOf(currentPage));
        generator.writeStringField("totalPages", String.valueOf(totalPages));

        generator.writeEndObject();
        generator.writeEndObject();
    }
}
