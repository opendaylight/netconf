/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.notifications;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.http.EventStreamListener;

final class SubscriptionEventListener implements EventStreamListener {

    @Override
    public void onStreamStart() {
        // EventStreamService#startEventStream(.., this, ..) returns streamControl
    }

    @Override
    public void onEventField(@NonNull final String fieldName, @NonNull final String fieldValue) {
        // emit DefaultHttpContent
    }

    @Override
    public void onStreamEnd() {
        // streamControl.close()
    }
}
