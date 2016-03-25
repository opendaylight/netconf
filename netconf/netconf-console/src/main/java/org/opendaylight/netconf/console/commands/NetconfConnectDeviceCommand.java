/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.commands;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.AbstractAction;
import org.opendaylight.netconf.console.api.NetconfConsoleProvider;

@Command(name = "netconf:connect-device", scope = "netconf", description = "Connect to a netconf device.")
public class NetconfConnectDeviceCommand extends AbstractAction {

    protected NetconfConsoleProvider service;

    public NetconfConnectDeviceCommand(NetconfConsoleProvider service) {
        this.service = service;
    }

    @Argument(index = 0, name = "ip", description = "Device IP", required = true, multiValued = false)
    String deviceIp;

    @Argument(index = 1, name = "port", description = "Device Port", required = true, multiValued = false)
    String devicePort;

    @Argument(index = 2, name = "port", description = "Username", required = true, multiValued = false)
    String username;

    @Argument(index = 3, name = "port", description = "Password", required = true, multiValued = false)
    String password;

    @Argument(index = 4, name = "tcp-only", description = "TCP/SSH", required = false, multiValued = false)
    Boolean tcpOnly;

    @Override
    protected Object doExecute() throws Exception {
        tcpOnly = (tcpOnly == null) ? false : tcpOnly;
        boolean status = service.connectDevice(deviceIp, devicePort, username, password, tcpOnly);
        return status;
    }
}
