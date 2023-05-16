/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.rest.doc.openapi;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Schema extends ObjectNode {
    private static final long serialVersionUID = 1L;
    ObjectNode discriminator;
    ObjectNode xml;
    ObjectNode externalDocs;
    ObjectNode example;

    public Schema(JsonNodeFactory nc) {
        super(nc);
    }

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

    public ObjectNode getExample() {
        return example;
    }

    public void setExample(ObjectNode example) {
        this.example = example;
    }
}
