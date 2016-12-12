/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.mount;

import com.google.common.net.InetAddresses;
import org.opendaylight.netconf.callhome.protocol.CallHomeProtocolSessionContext;

public enum SocketAddressAsAlias implements CallHomeSessionAliasProvider {

    INSTANCE;

    @Override
    public String getAlias(CallHomeProtocolSessionContext session) {
        return InetAddresses.toAddrString(session.getRemoteAddress().getAddress()) + ":" + session.getRemoteAddress().getPort();
    }
}
