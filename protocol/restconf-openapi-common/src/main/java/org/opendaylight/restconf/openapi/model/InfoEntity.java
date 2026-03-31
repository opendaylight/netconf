/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import org.eclipse.jdt.annotation.NonNull;

public final class InfoEntity extends OpenApiEntity {
    private final @NonNull String title;
    private static final String DESCRIPTION = """
        We are providing full API for configurational data which can be edited (by POST, PUT, PATCH and DELETE).
        For operational data we only provide GET API.

        For majority of request you can see only config data in examples. That's because we can show only one example
        per request. The exception when you can see operational data in example is when data are representing
        operational (config false) container with no config data in it.""";

    public InfoEntity(final @NonNull String title) {
        this.title = requireNonNull(title);
    }

    @Override
    public void generate(@NonNull JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart("info");
        generator.writeStringField("version", "1.0.0");
        generator.writeStringField("title", title);
        generator.writeStringField("description", DESCRIPTION);
        generator.writeEndObject();
    }
}
