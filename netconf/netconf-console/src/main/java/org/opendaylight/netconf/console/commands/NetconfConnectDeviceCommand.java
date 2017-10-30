/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.commands;

import com.google.common.annotations.VisibleForTesting;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.AbstractAction;
import org.opendaylight.netconf.console.api.NetconfCommands;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPasswordBuilder;

@Command(name = "netconf:connect-device", scope = "netconf", description = "Connect to a netconf device.")
public class NetconfConnectDeviceCommand extends AbstractAction {

    protected final NetconfCommands service;

    public NetconfConnectDeviceCommand(final NetconfCommands service) {
        this.service = service;
    }

    @VisibleForTesting
    NetconfConnectDeviceCommand(final NetconfCommands service, final String deviceIp, final String devicePort) {
        this.service = service;
        this.deviceIp = deviceIp;
        this.devicePort = devicePort;
    }

    @Option(name = "-i",
            aliases = { "--ipaddress" },
            description = "IP address of the netconf device",
            required = true,
            multiValued = false)
    private String deviceIp;

    @Option(name = "-p",
            aliases = { "--port" },
            description = "Port of the netconf device",
            required = true,
            multiValued = false)
    private String devicePort;

    @Option(name = "-U",
            aliases = { "--username" },
            description = "Username for netconf connection",
            required = true,
            multiValued = false)
    private String username;

    @Option(name = "-P",
            aliases = { "--password" },
            description = "Password for netconf connection",
            required = true,
            multiValued = false)
    private String password;

    @Option(name = "-t",
            aliases = { "--tcp-only" },
            description = "Type of connection, true for tcp only",
            required = false,
            multiValued = false)
    private String connectionType = "false";

    @Option(name = "-sl",
            aliases = { "--schemaless" },
            description = "Schemaless surpport, true for schemaless",
            required = false,
            multiValued = false)
    private String schemaless = "false";

    @Option(name = "-id",
            aliases = { "--identifier" },
            description = "Node Identifier of the netconf device",
            required = false,
            multiValued = false)
    private String deviceId;

    @Override
    protected Object doExecute() throws Exception {
        if (!NetconfCommandUtils.isIpValid(deviceIp) || !NetconfCommandUtils.isPortValid(devicePort)) {
            return "Invalid IP:" + deviceIp + " or Port:" + devicePort + "Please enter a valid entry to proceed.";
        }

        final boolean isTcpOnly = connectionType.equals("true");
        final boolean isSchemaless = schemaless.equals("true");
        final Credentials credentials =
                new LoginPasswordBuilder().setPassword(password).setUsername(username).build();

        final NetconfNode netconfNode = new NetconfNodeBuilder()
                                        .setHost(new Host(new IpAddress(new Ipv4Address(deviceIp))))
                                        .setPort(new PortNumber(Integer.decode(devicePort)))
                                        .setTcpOnly(isTcpOnly)
                                        .setSchemaless(isSchemaless)
                                        .setCredentials(credentials)
                                        .build();

        service.connectDevice(netconfNode, deviceId);
        final String message = "Netconf connector added succesfully";
        return message;
    }
}
