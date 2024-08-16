/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
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
import java.util.ArrayList;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.MediaType;
import org.eclipse.jdt.annotation.NonNull;

public final class GetRootEntity extends GetEntity {
    private static final String OPERATIONS = "operations";
    private static final String DATA = "data";
    private static final String CONTROLLER = "Controller";
    private static final String NETCONF_STATE = "#/components/schemas/ietf-netconf-monitoring_netconf-state";
    private static final String MODULES_STATE = "#/components/schemas/ietf-yang-library_modules-state";

    private final String type;

    public GetRootEntity(final @NonNull String deviceName, final @NonNull String type) {
        super(null, deviceName, "", new ArrayList<>(), null, false);
        this.type = requireNonNull(type);
    }

    @Override
    public void generate(final @NonNull JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart("get");
        if (type.equals(DATA)) {
            if (deviceName().equals(CONTROLLER)) {
                generator.writeStringField(DESCRIPTION,
                    "Result of this GET contains YANG module monitoring information.");
            } else {
                generator.writeStringField(DESCRIPTION, """
                    Result of this GET contains data from the monitoring data model, i.e., several lists that have
                    information about capabilities supported by the server, configuration datastores, data model schemas
                    supported by the server, some session-specific data and statistical data.""");
            }
        } else {
            generator.writeStringField(DESCRIPTION, """
                The example demonstrates only top-level container "ietf-restconf:operations".
                The request returns a list of all available operations on the mounted
                host, showcasing the structure of RPCs that can be executed.""");
        }
        generator.writeObjectFieldStart(RESPONSES);
        generator.writeObjectFieldStart(String.valueOf(OK.getStatusCode()));
        generator.writeStringField(DESCRIPTION, "OK");
        generator.writeObjectFieldStart(CONTENT);
        generator.writeObjectFieldStart(MediaType.APPLICATION_JSON);
        generator.writeObjectFieldStart(SCHEMA);
        generator.writeObjectFieldStart(PROPERTIES);
        if (type.equals(DATA)) {
            if (deviceName().equals(CONTROLLER)) {
                generator.writeObjectFieldStart("ietf-yang-library:modules-state");
                generator.writeStringField(TYPE, OBJECT);
                generator.writeStringField(REF, MODULES_STATE);
            } else {
                generator.writeObjectFieldStart("ietf-netconf-monitoring:netconf-state");
                generator.writeStringField(TYPE, OBJECT);
                generator.writeStringField(REF, NETCONF_STATE);
            }
            generator.writeEndObject(); // end of state
        }
        if (type.equals(OPERATIONS)) {
            generator.writeObjectFieldStart("ietf-restconf:operations");
            generator.writeStringField(TYPE, OBJECT);
            generator.writeEndObject(); // end of ietf-restconf:operations
        }
        generator.writeEndObject(); // end of properties
        generator.writeEndObject(); // end of json schema
        generator.writeEndObject(); //end of json
        generator.writeObjectFieldStart(MediaType.APPLICATION_XML);
        generator.writeObjectFieldStart(SCHEMA);
        if (type.equals(DATA)) {
            if (deviceName().equals(CONTROLLER)) {
                generator.writeStringField(REF, MODULES_STATE);
            } else {
                generator.writeStringField(REF, NETCONF_STATE);
            }
        }
        if (type.equals(OPERATIONS)) {
            generator.writeStringField(TYPE, OBJECT);
            // Define the root XML element and namespace
            generator.writeObjectFieldStart("xml");
            generator.writeStringField(NAME, OPERATIONS);
            generator.writeStringField("namespace", "urn:ietf:params:xml:ns:yang:ietf-restconf");
            generator.writeEndObject(); // end of xml for root
        }
        generator.writeEndObject(); // end of xml schema
        generator.writeEndObject(); // end of xml
        generator.writeEndObject(); //end of content
        generator.writeEndObject(); //end of 200
        generator.writeEndObject(); // end of responses
        final var summary = HttpMethod.GET + " - " + deviceName() + " - datastore - " + type;
        generator.writeStringField(SUMMARY, summary);
        generator.writeArrayFieldStart("tags");
        generator.writeString(deviceName() + " root");
        generator.writeEndArray(); //end of tags
        if (!type.equals(OPERATIONS)) {
            generateParams(generator);
        }
        generator.writeEndObject(); //end of get
    }
}
