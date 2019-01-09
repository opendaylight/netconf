/*
 * Copyright (c) 2019 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.api;

import java.net.InetAddress;

/**
 * Configuration for the RESTCONF server.
 *
 * @author Michael Vorburger.ch
 */
public interface RestConfConfig {

    /**
     * IP interface which the WebSocket server will listen on.
     */
    default InetAddress webSocketAddress() {
        return InetAddress.getLoopbackAddress();
    }

    /**
     * TCP port which the WebSocket server will listen on.
     */
    int webSocketPort();
}
