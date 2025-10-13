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
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;

public final class ServersEntity extends OpenApiEntity {
    private final @NonNull List<ServerEntity> servers;

    public ServersEntity(final @NonNull List<ServerEntity> servers) {
        this.servers = requireNonNull(servers);
    }

    @Override
    public void generate(@NonNull final JsonGenerator generator) throws IOException {
        generator.writeArrayFieldStart("servers");
        for (final var server : servers) {
            server.generate(generator);
        }
        generator.writeEndArray();
    }
}
