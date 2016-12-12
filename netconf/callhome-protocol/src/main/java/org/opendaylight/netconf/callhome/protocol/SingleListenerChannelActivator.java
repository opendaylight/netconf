/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.protocol;

import org.opendaylight.netconf.client.NetconfClientSessionListener;

class SingleListenerChannelActivator implements CallHomeNetconfSubsystemListener {

    private final NetconfClientSessionListener listener;

    public SingleListenerChannelActivator(NetconfClientSessionListener listener) {
        this.listener = listener;
    }

    @Override
    public void onNetconfSubsystemOpened(CallHomeProtocolSessionContext session, CallHomeChannelActivator activator) {
        activator.activate(listener);
    }

}