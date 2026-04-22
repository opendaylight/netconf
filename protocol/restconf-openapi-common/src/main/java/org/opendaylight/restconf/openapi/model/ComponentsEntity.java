/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.openapi.model.security.Http;
import org.opendaylight.restconf.openapi.model.security.Oauth2;
import org.opendaylight.restconf.openapi.model.security.SecuritySchemeObject;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;

public final class ComponentsEntity extends OpenApiEntity {
    private static final String BASIC_AUTH_NAME = "basicAuth";
    private static final Http OPEN_API_BASIC_AUTH = new Http("basic", null, null);
    private static final String OAUTH2_NAME = "oauth2";

    private final SchemasEntity schemas;
    private final SecuritySchemesEntity securitySchemes;

    public ComponentsEntity(final EffectiveModelContext modelContext, final Collection<? extends Module> modules,
            final boolean isForSingleModule, final int width, final int depth,
            final @Nullable OpenApiOauth2Configuration oauth2Config) {
        schemas = new SchemasEntity(modelContext, modules, isForSingleModule, width, depth);

        final Map<String, SecuritySchemeObject> schemes = new HashMap<>();
        schemes.put(BASIC_AUTH_NAME, OPEN_API_BASIC_AUTH);
        if (oauth2Config != null) {
            schemes.put(OAUTH2_NAME, new Oauth2("authorizationCode",
                oauth2Config.authorizationUrl(), oauth2Config.tokenUrl()));
        }
        securitySchemes = new SecuritySchemesEntity(Map.copyOf(schemes));
    }

    @Override
    public void generate(final JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart("components");
        schemas.generate(generator);
        securitySchemes.generate(generator);
        generator.writeEndObject();
    }
}
