/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.rest.doc.openapi;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Schema {
    private ArrayNode schemaEnum;
    private ArrayNode required;
    private ObjectNode discriminator;
    private ObjectNode examples;
    private ObjectNode externalDocs;
    private ObjectNode properties;
    private ObjectNode xml;
    private String description;
    private String ref;
    private String title;
    private String type;

    public ObjectNode getDiscriminator() {
        return discriminator;
    }

    public void setDiscriminator(ObjectNode discriminator) {
        this.discriminator = discriminator;
    }

    public ObjectNode getXml() {
        return xml;
    }

    public void setXml(ObjectNode xml) {
        this.xml = xml;
    }

    public ObjectNode getExternalDocs() {
        return externalDocs;
    }

    public void setExternalDocs(ObjectNode externalDocs) {
        this.externalDocs = externalDocs;
    }

    public ObjectNode getExamples() {
        return examples;
    }

    public void setExamples(ObjectNode examples) {
        this.examples = examples;
    }

    public ObjectNode getProperties() {
        return properties;
    }

    public void setProperties(ObjectNode properties) {
        this.properties = properties;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ArrayNode getRequired() {
        return required;
    }

    public void setRequired(ArrayNode required) {
        this.required = required;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public ArrayNode getEnum() {
        return schemaEnum;
    }

    public void setEnum(ArrayNode enumKey) {
        this.schemaEnum = enumKey;
    }
}
