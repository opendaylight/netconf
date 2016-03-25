/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.commands;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.AbstractAction;

@Command(name = "netconf:disconnect-device", scope = "netconf", description = "Disconnect netconf device.")
public class NetconfDisconnectDeviceCommand extends AbstractAction {

    @Override
    protected Object doExecute() throws Exception {
        // TODO Auto-generated method stub
        return null;
    }

}
