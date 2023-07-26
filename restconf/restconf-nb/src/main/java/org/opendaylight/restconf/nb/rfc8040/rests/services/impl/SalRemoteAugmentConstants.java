/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping;
import org.opendaylight.yangtools.yang.common.QName;

/**
 * Constants related to {@code sal-remote-augment.yang}.
 */
final class SalRemoteAugmentConstants {
    static final QName DATASTORE_QNAME = QName.create(NotificationOutputTypeGrouping.QNAME, "datastore").intern();
    static final QName SCOPE_QNAME = QName.create(NotificationOutputTypeGrouping.QNAME, "scope").intern();
    static final QName OUTPUT_TYPE_QNAME =
        QName.create(NotificationOutputTypeGrouping.QNAME, "notification-output-type").intern();

    private SalRemoteAugmentConstants() {
        // Hidden on purpose
    }
}
