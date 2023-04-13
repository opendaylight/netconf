/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.openapi;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

public final class OpenApiObject {
    private String openapi;
    private Info info;
    private ObjectNode externalDocs;
    private List<Server> servers;
    private ObjectNode tags;
    private ObjectNode paths;
    private Components components;

    public String getOpenapi() {
        return openapi;
    }

    public void setOpenapi(String openapi) {
        this.openapi = openapi;
    }

    public Info getInfo() {
        return info;
    }

    public void setInfo(Info info) {
        this.info = info;
    }

    public ObjectNode getExternalDocs() {
        return externalDocs;
    }

    public void setExternalDocs(ObjectNode externalDocs) {
        this.externalDocs = externalDocs;
    }

    public List<Server> getServers() {
        return servers;
    }

    public void setServers(List<Server> servers) {
        this.servers = servers;
    }

    public ObjectNode getTags() {
        return tags;
    }

    public void setTags(ObjectNode tags) {
        this.tags = tags;
    }

    public ObjectNode getPaths() {
        return paths;
    }

    public void setPaths(ObjectNode paths) {
        this.paths = paths;
    }

    public Components getComponents() {
        return components;
    }

    public void setComponents(Components components) {
        this.components = components;
    }
}
