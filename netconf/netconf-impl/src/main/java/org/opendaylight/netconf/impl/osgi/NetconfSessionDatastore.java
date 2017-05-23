/*
 * Copyright (c) 2017 Frinx s.r.o, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.impl.osgi;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import java.util.Map;
import org.opendaylight.netconf.impl.NetconfServerSession;

public class NetconfSessionDatastore {
    private final Map<Long, Map.Entry<NetconfServerSession, NetconfOperationRouter>> sessionDatastore = Maps.newConcurrentMap();

    public void put(NetconfServerSession session, NetconfOperationRouter operationRouter) {
        sessionDatastore.put(session.getSessionId(),Maps.immutableEntry(session,operationRouter));
    }

    public Optional<Map.Entry<NetconfServerSession, NetconfOperationRouter>> getSessionDatastore(long sessionId){
        return Optional.fromNullable(sessionDatastore.get(sessionId));
    }
}
