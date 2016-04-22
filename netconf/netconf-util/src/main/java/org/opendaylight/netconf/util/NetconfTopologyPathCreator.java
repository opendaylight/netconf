/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.util;

public class NetconfTopologyPathCreator {

    public static final String CLUSTERED_DEVICE_SOURCES_RESOLVER = "clusteredDeviceSourcesResolver";
    public static final String MASTER_SOURCE_PROVIDER
            = "masterSourceProvider";

    private static final String USER = "/user/";

    private String mainPath;

    public NetconfTopologyPathCreator(final String topologyId) {
        mainPath = createMainPath("", topologyId);
    }

    public NetconfTopologyPathCreator(final String memberAddress, final String topologyId) {
        mainPath = createMainPath(memberAddress, topologyId);
    }

    private String createMainPath(final String memberAddress, final String topologyId) {
        return memberAddress + USER + topologyId;
    }

    public NetconfTopologyPathCreator withSuffix(final String suffix) {
        mainPath += "/"+suffix;
        return this;
    }

    public String build(){
        return mainPath;
    }

}
