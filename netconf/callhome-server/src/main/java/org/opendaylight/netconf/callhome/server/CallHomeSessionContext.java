/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.server;

import org.opendaylight.netconf.client.NetconfClientSessionListener;


/**
 * Session Context for incoming Call-Home connections.
 */
public interface CallHomeSessionContext extends AutoCloseable {

    /**
     * Returns unique context identifier .
     *
     * @return Returns application-provided session context identifier
     */
    String id();

    NetconfClientSessionListener netconfSessionListener();

    void close();
}
