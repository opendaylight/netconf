/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.utils;

import java.util.Collection;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationListener;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

/**
 * Holds information about registered notifications listeners.
 */
public class ListenerRegistrationHolder {

    private final DOMNotificationListener domNotificationListener;
    private final Collection<SchemaPath> types;

    public ListenerRegistrationHolder(final DOMNotificationListener domNotificationListener,
                                      final Collection<SchemaPath> types) {
        this.domNotificationListener = domNotificationListener;
        this.types = types;
    }

    public DOMNotificationListener getDomNotificationListener() {
        return domNotificationListener;
    }

    public Collection<SchemaPath> getTypes() {
        return types;
    }


}
