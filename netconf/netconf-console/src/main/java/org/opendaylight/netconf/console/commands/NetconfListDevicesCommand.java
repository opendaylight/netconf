/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.commands;

import java.util.List;
import java.util.Map;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.AbstractAction;
import org.opendaylight.netconf.console.api.NetconfConsoleProvider;

@Command(name = "netconf:list-devices", scope = "netconf", description = "List all netconf devices in the topology.")
public class NetconfListDevicesCommand extends AbstractAction {

    protected NetconfConsoleProvider service;
    Boolean isConfigurationDatastore = false;

    public NetconfListDevicesCommand(NetconfConsoleProvider service) {
        this.service = service;
    }

    @Override
    protected Object doExecute() throws Exception {
        Map<String, Map<String, String>> allDevices = service.listDevices(isConfigurationDatastore);
        // TODO format the strings as needed
        return allDevices;
    }
}
