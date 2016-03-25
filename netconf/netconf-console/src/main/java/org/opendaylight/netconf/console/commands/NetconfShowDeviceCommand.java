/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.commands;

import java.util.Map;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.AbstractAction;
import org.opendaylight.netconf.console.api.NetconfConsoleProvider;

@Command(name = "netconf:show-device", scope = "netconf", description = "Shows netconf device attributes.")
public class NetconfShowDeviceCommand extends AbstractAction {

    protected NetconfConsoleProvider service;

    public NetconfShowDeviceCommand(NetconfConsoleProvider service) {
        this.service = service;
    }

    @Argument(index = 0, name = "ip", description = "Device IP", required = true, multiValued = false)
    String deviceIp;

    @Override
    protected Object doExecute() throws Exception {
        Map<String, Map<String, String>> device = service.showDevice(deviceIp);
        return device;
    }
}
