/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.console.commands;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.opendaylight.netconf.console.api.NetconfCommands;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.parameters.Protocol.Name;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.parameters.ProtocolBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.parameters.protocol.specification.TlsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.parameters.protocol.specification.TlsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.parameters.protocol.specification.tls._case.TlsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNodeBuilder;
import org.opendaylight.yangtools.yang.common.Uint16;

@Service
@Command(name = "connect-device", scope = "netconf", description = "Connect to a netconf device.")
public class NetconfConnectDeviceCommand implements Action {

    @Reference
    private NetconfCommands service;

    public NetconfConnectDeviceCommand() {

    }

    @VisibleForTesting
    NetconfConnectDeviceCommand(final NetconfCommands service) {
        this.service = service;
    }

    @VisibleForTesting
    NetconfConnectDeviceCommand(final NetconfCommands service, final String deviceIp, final String devicePort,
            final String username, final String password) {
        this.service = requireNonNull(service);
        this.deviceIp = requireNonNull(deviceIp);
        this.devicePort = requireNonNull(devicePort);
        this.username = requireNonNull(username);
        this.password = requireNonNull(password);
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
            required = false,
            censor = true,
            multiValued = false)
    private String username;

    @Option(name = "-P",
            aliases = { "--password" },
            description = "Password for netconf connection",
            required = false,
            censor = true,
            multiValued = false)
    private String password;

    @Option(name = "-t",
            aliases = { "--tcp-only" },
            description = "Type of connection, true for tcp only",
            required = false,
            multiValued = false)
    private String connectionType = "false";

    @Option(name = "-pr",
            aliases = { "--protocol" },
            description = "Which protocol to be used, ssh or tls",
            required = false,
            multiValued = false)
    private String protocol = "ssh";

    @Option(name = "-ev",
            aliases = { "--excluded-versions" },
            description = "TLS versions not supported by target device",
            required = false,
            multiValued = false)
    private String excludedTlsVersions;

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
    public Object execute() {
        if (!NetconfCommandUtils.isIpValid(deviceIp) || !NetconfCommandUtils.isPortValid(devicePort)) {
            return "Invalid IP:" + deviceIp + " or Port:" + devicePort + "Please enter a valid entry to proceed.";
        }

        final boolean isTcpOnly = connectionType.equals("true");
        final boolean isSchemaless = schemaless.equals("true");

        final NetconfNodeBuilder netconfNodeBuilder = new NetconfNodeBuilder();
        netconfNodeBuilder.setHost(new Host(new IpAddress(new Ipv4Address(deviceIp))))
                          .setPort(new PortNumber(Uint16.valueOf(Integer.decode(devicePort))))
                          .setTcpOnly(isTcpOnly)
                          .setSchemaless(isSchemaless);

        if (isTcpOnly || protocol.equalsIgnoreCase("ssh")) {
            if (Strings.isNullOrEmpty(username) || Strings.isNullOrEmpty(password)) {
                return "Empty Username:" + username + " or Password:" + password
                        + ". In TCP or SSH mode, you must provide valid username and password.";
            }
            netconfNodeBuilder.setCredentials(new LoginPasswordBuilder()
                .setUsername(username)
                .setPassword(password)
                .build());
            if (!isTcpOnly) {
                netconfNodeBuilder.setProtocol(new ProtocolBuilder().setName(Name.SSH).build());
            }
        } else if (protocol.equalsIgnoreCase("tls")) {
            TlsCase tlsCase = null;
            if (!Strings.isNullOrEmpty(excludedTlsVersions)) {
                tlsCase = new TlsCaseBuilder()
                            .setTls(new TlsBuilder()
                                    .setExcludedVersions(ImmutableSet.copyOf(excludedTlsVersions.split(","))).build())
                            .build();
            }
            netconfNodeBuilder.setProtocol(new ProtocolBuilder()
                                            .setName(Name.TLS)
                                            .setSpecification(tlsCase)
                                            .build());
        } else {
            return "Invalid protocol: " + protocol + ". Only SSH and TLS are supported.";
        }

        service.connectDevice(netconfNodeBuilder.build(), deviceId);
        final String message = "Netconf connector added succesfully";
        return message;
    }
}
