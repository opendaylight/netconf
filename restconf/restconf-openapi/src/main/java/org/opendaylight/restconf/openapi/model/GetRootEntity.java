/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.Response.Status.OK;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import javax.ws.rs.HttpMethod;
import org.eclipse.jdt.annotation.NonNull;

public final class GetRootEntity extends GetEntity {
    private final String type;

    public GetRootEntity(final @NonNull String deviceName, final @NonNull String type) {
        super(null, deviceName, "", null, null, false);
        this.type = requireNonNull(type);
    }

    @Override
    public void generate(final @NonNull JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart("get");
        generator.writeStringField(DESCRIPTION, type.equals("data")
            ? "Queries the config (startup) datastore on the mounted hosted."
            : "Queries the available operations (RPC calls) on the mounted hosted.");
        generator.writeObjectFieldStart(RESPONSES);
        generator.writeObjectFieldStart(String.valueOf(OK.getStatusCode()));
        generator.writeStringField(DESCRIPTION, "OK");
        generator.writeEndObject(); //end of 200
        generator.writeEndObject(); // end of responses
        final var summary = HttpMethod.GET + " - " + deviceName() + " - datastore - " + type;
        generator.writeStringField(SUMMARY, summary);
        generator.writeArrayFieldStart("tags");
        generator.writeString(deviceName() + " GET root");
        generator.writeEndArray(); //end of tags
        generator.writeEndObject(); //end of get
    }
}
