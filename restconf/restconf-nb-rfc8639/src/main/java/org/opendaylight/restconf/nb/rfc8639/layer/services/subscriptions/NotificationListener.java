/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationListener;

public class NotificationListener implements DOMNotificationListener {

    private final ReplayBuffer replayBuffer;

    public NotificationListener(final ReplayBuffer replayBuffer) {
        this.replayBuffer = requireNonNull(replayBuffer);
    }

    @Override
    public void onNotification(final @NonNull DOMNotification notification) {
        replayBuffer.addNotificationForTimeStamp(Instant.now(), notification);
    }
}
