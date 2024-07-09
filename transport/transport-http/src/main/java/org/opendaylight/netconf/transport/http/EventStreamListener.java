/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import org.eclipse.jdt.annotation.NonNull;

// FIXME document purpose
public interface EventStreamListener {

    void onStreamStart();

    void onEventField(@NonNull String fieldName, @NonNull String fieldValue);

    default void onEventComment(@NonNull String comment) {
        // ignored by default
    }

    void onStreamEnd();
}
