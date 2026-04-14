/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger;

public class Main {
    private final RestconfNetconfFactory factory = DaggerRestconfNetconfFactory.create();

    void main() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        factory.nettyEndpoint();
        factory.netconfTopologyImpl();
    }

    void shutdown() {
        this.factory.close();
    }
}
