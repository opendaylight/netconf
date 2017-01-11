/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol;

import org.opendaylight.netconf.client.NetconfClientSessionListener;

/**
 *
 * Listener for successful opening of NETCONF channel on incoming Call Home connections.
 *
 */
public interface CallHomeNetconfSubsystemListener {

    /**
     * Invoked when Netconf Subsystem was successfully opened on incoming SSH Call Home connection.
     *
     * Implementors of this method should use provided {@link CallHomeChannelActivator} to attach
     * {@link NetconfClientSessionListener} to session and to start NETCONF client session negotiation.
     *
     *
     * @param session Incoming Call Home session on which NETCONF subsystem was successfully opened
     * @param activator Channel Activator to be used in order to start NETCONF Session negotiation.
     */
    void onNetconfSubsystemOpened(CallHomeProtocolSessionContext session, CallHomeChannelActivator activator);

}
