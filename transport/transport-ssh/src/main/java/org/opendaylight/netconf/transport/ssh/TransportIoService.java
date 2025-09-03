/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import org.opendaylight.netconf.shaded.sshd.common.io.IoHandler;

final class TransportIoService {
    private final IoHandler handler;

    TransportIoService(final IoHandler handler) {
        this.handler = handler;
    }

    TransportIoSession createSession() {
        return new TransportIoSession(handler);
    }
}
