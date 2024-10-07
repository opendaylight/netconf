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
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;

public final class MetadataEntity extends OpenApiEntity {
    private static final String TOTAL_MODULES = "totalModules";
    private static final String CONFIG_MODULES = "configModules";
    private static final String NON_CONFIG_MODULES = "nonConfigModules";
    private static final String CURRENT_PAGE = "currentPage";
    private static final String TOTAL_PAGES = "totalPages";

    private final @NonNull Map<String, ?> mappedMetadata;

    public MetadataEntity(final int offset, final int limit, final long allModules, final long configModules) {
        mappedMetadata = limit == 0 && offset == 0
            ? Map.of(
                TOTAL_MODULES, allModules,
                CONFIG_MODULES, configModules,
                NON_CONFIG_MODULES, allModules - configModules,
                CURRENT_PAGE, 1,
                TOTAL_PAGES, 1)
            : Map.of(
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

    @Override
    public void generate(final @NonNull JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        generator.writeObjectFieldStart("metadata");
        for (final var entry : mappedMetadata.entrySet()) {
            generator.writeStringField(entry.getKey(), entry.getValue().toString());
        }
        generator.writeEndObject();
        generator.writeEndObject();
    }
}
