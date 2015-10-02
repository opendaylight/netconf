/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology;

import com.google.common.annotations.Beta;
import java.util.List;

/**
 *
 */
@Beta
public interface Peer<T extends Peer<T>> {

    boolean isMaster();

    Iterable<T> getPeers();

    /**
     * Used for communication between NodeAdministratorCallbacks
     */
    public interface PeerContext<M> {

        // TODO the message needs to be serialized and sent through AKKA, how to achieve it ?
        void notifyPeers(M msg);

    }
}
