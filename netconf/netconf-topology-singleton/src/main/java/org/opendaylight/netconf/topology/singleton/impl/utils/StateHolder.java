/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is used for holding important information if master is changed and new connection is initiated.
 * When master is down, new master lacks of information of previous master. The same situation for slaves, when master
 * is down new slave mount point is created but lacks for information of state before.
 */
public class StateHolder {

    private static final List<ListenerRegistrationHolder> LISTENER_REGISTRATIONS = new ArrayList<>();
    private static final List<String> NETCONF_DEVICE_REGISTERED_STREAMS = new ArrayList<>();

    public void add(final ListenerRegistrationHolder listenerRegistration) {
        if (!LISTENER_REGISTRATIONS.contains(listenerRegistration)) {
            LISTENER_REGISTRATIONS.add(listenerRegistration);
        }
    }

    public List<ListenerRegistrationHolder> getListenerRegistrations(){
        return LISTENER_REGISTRATIONS;
    }

    public void addNotificationStream(final String streams) {
        if (!NETCONF_DEVICE_REGISTERED_STREAMS.contains(streams)) {
            NETCONF_DEVICE_REGISTERED_STREAMS.add(streams);
        }
    }

    public List<String> getNetconfDeviceRegisteredStreams() {
        return NETCONF_DEVICE_REGISTERED_STREAMS;
    }
}
