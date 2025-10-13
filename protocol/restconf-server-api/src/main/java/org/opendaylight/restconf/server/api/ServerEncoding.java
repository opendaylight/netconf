/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.wg.server.api.RestconfMonitoringEncoding;
import org.opendaylight.yangtools.yang.common.QName;

/**
 * The concept of a server request encoding. This is somewhat of a leaky abstraction in RFC8639, which we are hijacking
 * to establish a consistent view of what is a consistent view on on what encodings a server supports. We tie in
 * RFC8040's other attempt at this, which is the {@code encoding} leaf in {@code ietf-restconf-monitoring.yang}.
 *
 * <p>At the end of the day, the only contract his interface defines is that it has two representations for
 * an {@code encoding} leaf in different models.
 * <ul>
 *   <li>the {@code ietf-restconf-monitoring.yang} view as a plain {@code type string}</li>
 *   <li>the {@code ietf-subscribed-notifications.yang} view as a {@code identityref} based of
 *       {@code identity encoding}</li>
 * </ul>
 */
@NonNullByDefault
public interface ServerEncoding {
    /**
     * {@return the value to use in {@code ietf-restconf-monitoring.yang} context}
     */
    RestconfMonitoringEncoding monitoringEncoding();

    /**
     * {@return the value to use in {@code ietf-subscribed-notification.yang} context}
     */
    QName notificationsEncoding();
}