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
import org.eclipse.jdt.annotation.NonNull;

public final class InfoEntity extends OpenApiEntity {
    private final String version;
    private final String title;
    private final String description;

    public InfoEntity(final String version, final String title, final String description) {
        this.version = version;
        this.title = title;
        this.description = description;
    }

    @Override
    public void generate(@NonNull JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart("info");
        if (version != null) {
            generator.writeStringField("version", version);
        }
        if (title != null) {
            generator.writeStringField("title", title);
        }
        if (description != null) {
            generator.writeStringField("description", description);
        }
        generator.writeEndObject();
    }
}
