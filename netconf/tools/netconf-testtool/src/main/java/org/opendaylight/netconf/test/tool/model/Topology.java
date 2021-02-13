/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Topology {

    @SerializedName("topology-id")
    private String topologyId;
    @SerializedName("node")
    private List<Node> nodeList;

    public Topology() {
    }

    public String getTopologyId() {
        return topologyId;
    }

    public void setTopologyId(final String topologyId) {
        this.topologyId = topologyId;
    }

    public List<Node> getNodeList() {
        return nodeList;
    }

    public void setNodeList(final List<Node> nodeList) {
        this.nodeList = nodeList;
    }
}
