/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger;

public class Main {

    private final RestconfNetconfFactory factory;

    public static void main(final String[] args) {
        final var restconfNetconfFactory = DaggerRestconfNetconfFactory.create();
        final var main = new Main(restconfNetconfFactory);
        main.startApp();
    }

    public Main(final RestconfNetconfFactory factory) {
        this.factory = factory;
    }

    public void startApp() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        factory.nettyEndpoint();
        factory.netconfTopologyImpl();
    }

    public void shutdown() {
        this.factory.close();
    }
}
