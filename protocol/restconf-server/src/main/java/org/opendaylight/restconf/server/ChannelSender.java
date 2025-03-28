/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import org.opendaylight.restconf.server.spi.RestconfStream.Sender;

/**
 * A {@link Sender} bound directly to the underlying transport channel for event streams over HTTP/1.
 */
public final class ChannelSender extends AbstractChannelSender {
    public ChannelSender(final int sseMaximumFragmentLength) {
        super(sseMaximumFragmentLength);
    }
}
