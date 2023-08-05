/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.odlparent.logging.markers.Markers;
import org.opendaylight.yangtools.concepts.AbstractRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A connection to a {@link RemoteDevice} established through a {@link RemoteDeviceHandler}. Instances of this class
 * need to be resource-managed through {@link #close()}.
 *
 * @apiNote
 *     This is not an interface because it is inherently related to some state -- hence this is an abstract class. We
 *     also
 */
public abstract class RemoteDeviceConnection extends AbstractRegistration {
    private static final Logger LOG = LoggerFactory.getLogger(RemoteDeviceConnection.class);

    // FIXME: document this node
    @SuppressWarnings("checkstyle:illegalCatch")
    public final void onNotification(final DOMNotification domNotification) {
        if (notClosed()) {
            try {
                onNotificationImpl(requireNonNull(domNotification));
            } catch (RuntimeException e) {
                LOG.warn("{} threw an exception", this, e);
                LOG.debug(Markers.confidential(), "... while handling {}", domNotification);
            }
        }
    }

    // FIXME: document this node
    protected abstract void onNotificationImpl(@NonNull DOMNotification domNotification);
}
