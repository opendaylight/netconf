/*
 * Copyright (c) 2026 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.callhome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.testutils.DataBrokerTestModule;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev251204.netconf.client.listen.stack.grouping.transport.SshBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev251204.netconf.client.listen.stack.grouping.transport.TlsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev251204.netconf.client.listen.stack.grouping.transport.ssh.ssh.SshClientParameters;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev251204.netconf.client.listen.stack.grouping.transport.ssh.ssh.SshClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev251204.netconf.client.listen.stack.grouping.transport.ssh.ssh.TcpServerParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev241010.ssh.client.grouping.TransportParamsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.transport.params.grouping.EncryptionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev241010.tcp.server.grouping.LocalBind;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev241010.tcp.server.grouping.LocalBindBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev260605.NetconfCallhomeServer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev260605.NetconfCallhomeServerBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev260605.netconf.callhome.server.GlobalBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev260605.netconf.callhome.server.global.Endpoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev260605.netconf.callhome.server.global.EndpointsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev260605.netconf.callhome.server.global.endpoints.Endpoint;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev260605.netconf.callhome.server.global.endpoints.EndpointBuilder;
import org.opendaylight.yangtools.binding.DataObjectIdentifier;
import org.opendaylight.yangtools.binding.util.BindingMap;
import org.opendaylight.yangtools.yang.common.Uint16;

class CallHomeListenEndpointsTest {
    private static final DataObjectIdentifier<NetconfCallhomeServer> ROOT_IID =
        DataObjectIdentifier.builder(NetconfCallhomeServer.class).build();

    private DataBroker dataBroker;

    @BeforeEach
    void beforeEach() {
        dataBroker = DataBrokerTestModule.dataBroker();
    }

    /** Reading the SSH bind returns the configured endpoint's address and port. */
    @Test
    void readSshBindReturnsConfiguredBind() throws Exception {
        writeEndpoints(endpoints(sshEndpoint(localBind("127.0.0.1", 4444), null)));

        final var bind = CallHomeListenEndpoints.readSshBind(dataBroker);
        assertNotNull(bind);
        assertEquals(4444, bind.getPort());
        assertEquals("127.0.0.1", bind.getAddress().getHostAddress());
    }

    /** Reading the TLS bind returns the configured endpoint's address and port. */
    @Test
    void readTlsBindReturnsConfiguredBind() throws Exception {
        writeEndpoints(endpoints(tlsEndpoint(localBind("127.0.0.2", 4445))));

        final var bind = CallHomeListenEndpoints.readTlsBind(dataBroker);
        assertNotNull(bind);
        assertEquals(4445, bind.getPort());
        assertEquals("127.0.0.2", bind.getAddress().getHostAddress());
        // no SSH endpoint configured
        assertNull(CallHomeListenEndpoints.readSshBind(dataBroker));
    }

    /** Each reader selects the endpoint whose transport matches. */
    @Test
    void readBindSelectsEndpointMatchingTransport() throws Exception {
        writeEndpoints(endpoints(
            sshEndpoint(localBind("127.0.0.1", 4444), null),
            tlsEndpoint(localBind("127.0.0.2", 4445))));

        assertEquals(4444, CallHomeListenEndpoints.readSshBind(dataBroker).getPort());
        assertEquals(4445, CallHomeListenEndpoints.readTlsBind(dataBroker).getPort());
    }

    /** The TLS bind is {@code null} when only an SSH endpoint is configured. */
    @Test
    void readTlsBindNullWhenOnlySshConfigured() throws Exception {
        writeEndpoints(endpoints(sshEndpoint(localBind("127.0.0.1", 4444), null)));
        assertNull(CallHomeListenEndpoints.readTlsBind(dataBroker));
    }

    /** The SSH bind is {@code null} when the endpoint has no TCP server parameters. */
    @Test
    void readSshBindNullWhenNoLocalBind() throws Exception {
        // SSH endpoint present but without tcp-server-parameters
        writeEndpoints(endpoints(sshEndpoint(null, null)));
        assertNull(CallHomeListenEndpoints.readSshBind(dataBroker));
    }

    /** The SSH transport parameters are returned when configured on the endpoint. */
    @Test
    void readSshTransportParamsReturnsParams() throws Exception {
        writeEndpoints(endpoints(sshEndpoint(localBind("127.0.0.1", 4444), new SshClientParametersBuilder()
            .setTransportParams(new TransportParamsBuilder()
                .setEncryption(new EncryptionBuilder().build())
                .build())
            .build())));

        final var params = CallHomeListenEndpoints.readSshTransportParams(dataBroker);
        assertNotNull(params);
        assertNotNull(params.getEncryption());
    }

    /** The SSH transport parameters are {@code null} when not configured. */
    @Test
    void readSshTransportParamsNullWhenNotConfigured() throws Exception {
        writeEndpoints(endpoints(sshEndpoint(localBind("127.0.0.1", 4444), null)));
        assertNull(CallHomeListenEndpoints.readSshTransportParams(dataBroker));
    }

    /** All readers return {@code null} when the endpoints container is absent. */
    @Test
    void readReturnsNullWhenEndpointsAbsent() {
        assertNull(CallHomeListenEndpoints.readSshBind(dataBroker));
        assertNull(CallHomeListenEndpoints.readTlsBind(dataBroker));
        assertNull(CallHomeListenEndpoints.readSshTransportParams(dataBroker));
    }

    private void writeEndpoints(final Endpoints endpoints) throws Exception {
        final var root = new NetconfCallhomeServerBuilder()
            .setGlobal(new GlobalBuilder().setEndpoints(endpoints).build())
            .build();
        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.CONFIGURATION, ROOT_IID, root);
        tx.commit().get();
    }

    private static Endpoints endpoints(final Endpoint... endpoints) {
        return new EndpointsBuilder().setEndpoint(BindingMap.of(endpoints)).build();
    }

    private static LocalBind localBind(final String address, final int port) {
        return new LocalBindBuilder()
            .setLocalAddress(new IpAddress(new Ipv4Address(address)))
            .setLocalPort(new PortNumber(Uint16.valueOf(port)))
            .build();
    }

    private static Endpoint sshEndpoint(final LocalBind bind, final SshClientParameters sshParams) {
        final var tcp = bind == null ? null
            : new TcpServerParametersBuilder().setLocalBind(BindingMap.of(bind)).build();
        final var sshContainer = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client
            .rev251204.netconf.client.listen.stack.grouping.transport.ssh.SshBuilder()
            .setTcpServerParameters(tcp)
            .setSshClientParameters(sshParams)
            .build();
        return new EndpointBuilder()
            .setName("ssh-listen")
            .setTransport(new SshBuilder().setSsh(sshContainer).build())
            .build();
    }

    private static Endpoint tlsEndpoint(final LocalBind bind) {
        final var tcp = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev251204
            .netconf.client.listen.stack.grouping.transport.tls.tls.TcpServerParametersBuilder()
            .setLocalBind(BindingMap.of(bind))
            .build();
        final var tlsContainer = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client
            .rev251204.netconf.client.listen.stack.grouping.transport.tls.TlsBuilder()
            .setTcpServerParameters(tcp)
            .build();
        return new EndpointBuilder()
            .setName("tls-listen")
            .setTransport(new TlsBuilder().setTls(tlsContainer).build())
            .build();
    }
}
