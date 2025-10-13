/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
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

public final class ServerEntity extends OpenApiEntity {
    private final @NonNull String url;

    public ServerEntity(final @NonNull String url) {
        this.url = requireNonNull(url);
    }

    @Override
    public void generate(@NonNull final JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        generator.writeStringField("url", url);
        generator.writeEndObject();
    }
}
