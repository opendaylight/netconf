/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import org.opendaylight.netconf.shaded.sshd.common.io.AbstractIoWriteFuture;

final class TCPIoWriterFuture extends AbstractIoWriteFuture {
    TCPIoWriterFuture(final Object id) {
        super(id, null);
    }
}
