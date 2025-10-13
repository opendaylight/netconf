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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;

/**
 * An OpenAPI document.
 */
public final class DocumentEntity extends OpenApiEntity {
    private final InfoEntity info;
    private final ServersEntity servers;
    private final PathsEntity paths;
    private final ComponentsEntity components;
    private final SecurityEntity security;

    public DocumentEntity(final EffectiveModelContext modelContext, final String title, final String url,
            final List<Map<String, List<String>>> security, final String deviceName, final String urlPrefix,
            final boolean isForSingleModule, final boolean includeDataStore, final Collection<? extends Module> modules,
            final String basePath, final int width, final int depth) {
        info = new InfoEntity(title);
        servers = new ServersEntity(List.of(new ServerEntity(url)));
        paths = new PathsEntity(modelContext, deviceName, urlPrefix, isForSingleModule, includeDataStore, modules,
            basePath, width, depth);
        components = new ComponentsEntity(modelContext, modules, isForSingleModule, width, depth);
        this.security = new SecurityEntity(security);
    }

    @Override
    public void generate(final JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        new OpenApiVersionEntity().generate(generator);
        info.generate(generator);
        servers.generate(generator);
        paths.generate(generator);
        components.generate(generator);
        security.generate(generator);
        generator.writeEndObject();
    }
}
