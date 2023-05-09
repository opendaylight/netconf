/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.openapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Path {
    @JsonProperty("$ref")
    private String ref;
    private String summary;
    private String description;
    private ObjectNode get;
    private ObjectNode put;
    private ObjectNode post;
    private ObjectNode delete;
    private ObjectNode options;
    private ObjectNode head;
    private ObjectNode patch;
    private ObjectNode trace;
    private ObjectNode servers;

    public Path() {
        // just for fasterxml
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ObjectNode getGet() {
        return get;
    }

    public void setGet(ObjectNode get) {
        this.get = get;
    }

    public ObjectNode getPut() {
        return put;
    }

    public void setPut(ObjectNode put) {
        this.put = put;
    }

    public ObjectNode getPost() {
        return post;
    }

    public void setPost(ObjectNode post) {
        this.post = post;
    }

    public ObjectNode getDelete() {
        return delete;
    }

    public void setDelete(ObjectNode delete) {
        this.delete = delete;
    }

    public ObjectNode getOptions() {
        return options;
    }

    public void setOptions(ObjectNode options) {
        this.options = options;
    }

    public ObjectNode getHead() {
        return head;
    }

    public void setHead(ObjectNode head) {
        this.head = head;
    }

    public ObjectNode getPatch() {
        return patch;
    }

    public void setPatch(ObjectNode patch) {
        this.patch = patch;
    }

    public ObjectNode getTrace() {
        return trace;
    }

    public void setTrace(ObjectNode trace) {
        this.trace = trace;
    }

    public ObjectNode getServers() {
        return servers;
    }

    public void setServers(ObjectNode servers) {
        this.servers = servers;
    }
}
