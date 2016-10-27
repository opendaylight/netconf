/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.notifications;

import java.util.List;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceNotificationService;
import org.opendaylight.netconf.topology.singleton.impl.utils.ListenerRegistrationHolder;
import org.opendaylight.netconf.topology.singleton.impl.utils.StateHolder;

/**
 * When registrations are holded by state holder, this registrations are invoked, due to setting application back to
 * previous state and let notification running continue.
 */
public class MasterNotificationService extends NetconfDeviceNotificationService
        implements NetconfNotificationListenerRegistrar {

    public MasterNotificationService(final StateHolder stateHolder) {
        super();
        registerNotificationListeners(stateHolder.getListenerRegistrations());
        registerStreams(stateHolder.getNetconfDeviceRegisteredStreams());
    }

    @Override
    public void registerNotificationListeners(final List<ListenerRegistrationHolder> listeners) {
        listeners.forEach(listener -> registerNotificationListener(listener.getDomNotificationListener(),
                listener.getTypes()));
    }

    @Override
    public void registerStreams(final List<String> netconfDeviceRegisteredStreams) {
        /*
         *  TODO: Actually we have no information about streams due to testool does not support nc-notification model.
         *  TODO: On the other hands here should be invoked rpc create-subscription on all streams.
         */
    }

}
