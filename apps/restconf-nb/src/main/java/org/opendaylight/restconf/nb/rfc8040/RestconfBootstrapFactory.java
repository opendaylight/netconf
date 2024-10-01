/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import org.opendaylight.netconf.transport.tcp.BootstrapFactory;

final class RestconfBootstrapFactory extends BootstrapFactory {
    record Configuration(int bossThreads, int workerThreads) {
        // Nothing else
    }

    RestconfBootstrapFactory(final Configuration configuration) {
        super("odl-restconf-nb-worker", configuration.workerThreads, "odl-restconf-nb-boss", configuration.bossThreads);
    }
}
