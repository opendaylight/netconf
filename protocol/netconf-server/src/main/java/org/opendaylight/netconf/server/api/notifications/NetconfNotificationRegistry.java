/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.api.notifications;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.StreamNameType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.Streams;
import org.opendaylight.yangtools.concepts.Registration;

public interface NetconfNotificationRegistry {
    /**
     * Add listener for a certain notification type.
     */
    @NonNull Registration registerNotificationListener(@NonNull StreamNameType stream,
        @NonNull NetconfNotificationListener listener);

    /**
     * Check stream availability.
     */
    boolean isStreamAvailable(StreamNameType streamNameType);

    /**
     * Get all the streams available.
     */
    Streams getNotificationPublishers();
}
