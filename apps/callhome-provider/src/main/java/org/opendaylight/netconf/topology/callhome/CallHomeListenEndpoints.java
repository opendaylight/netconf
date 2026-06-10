/*
 * Copyright (c) 2026 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.callhome;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev251204.netconf.client.listen.stack.grouping.transport.Ssh;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev251204.netconf.client.listen.stack.grouping.transport.Tls;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.TransportParamsGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev241010.TcpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev260605.NetconfCallhomeServer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev260605.netconf.callhome.server.Global;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev260605.netconf.callhome.server.global.Endpoints;
import org.opendaylight.yangtools.binding.DataObjectIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper that derives the Call Home listener configuration from the standard IETF
 * {@code netconf-client-listen-stack-grouping} exposed under
 * {@code /netconf-callhome-server/global/endpoints}.
 *
 * <p>This is a minimal, startup-time integration: the configuration datastore is read once and the
 * first endpoint matching the requested transport is used to override the OSGi-provided settings.
 * When no matching endpoint is configured (or the datastore is not yet populated at activation time)
 * callers fall back to their static OSGi configuration.
 *
 * <p>Only the first matching endpoint is used because OpenDaylight currently opens a single listen
 * socket per transport (one SSH server and one TLS server). The datastore models a list of endpoints
 * to mirror the IETF {@code netconf-client-listen-stack-grouping} and to leave room for future
 * multi-listener support, until then, when several endpoints share a transport the one with the
 * lowest {@code name} (the list key) wins and the rest are ignored.
 */
@NonNullByDefault
final class CallHomeListenEndpoints {
    private static final Logger LOG = LoggerFactory.getLogger(CallHomeListenEndpoints.class);
    private static final DataObjectIdentifier<Endpoints> ENDPOINTS_II = DataObjectIdentifier
        .builder(NetconfCallhomeServer.class).child(Global.class).child(Endpoints.class).build();

    private CallHomeListenEndpoints() {
        // Hidden on purpose.
    }

    /**
     * {@return the SSH Call Home listener bind address, or {@code null} when no SSH endpoint is configured}
     */
    static @Nullable InetSocketAddress readSshBind(final DataBroker broker) {
        return readBind(broker, true);
    }

    /**
     * {@return the TLS Call Home listener bind address, or {@code null} when no TLS endpoint is configured}
     */
    static @Nullable InetSocketAddress readTlsBind(final DataBroker broker) {
        return readBind(broker, false);
    }

    /**
     * Resolves the SSH transport parameters of the first configured SSH endpoint.
     *
     * @return the host-key/key-exchange/encryption/MAC algorithms of the first configured SSH endpoint, or
     *     {@code null} when none is configured
     */
    static @Nullable TransportParamsGrouping readSshTransportParams(final DataBroker broker) {
        final var endpoints = readEndpoints(broker);
        if (endpoints != null) {
            for (var endpoint : endpoints.nonnullEndpoint().values()) {
                if (endpoint.getTransport() instanceof Ssh sshCase && sshCase.getSsh() != null) {
                    final var params = sshCase.getSsh().getSshClientParameters();
                    final var transportParams = params == null ? null : params.getTransportParams();
                    if (transportParams != null) {
                        LOG.info("Using Call Home SSH transport parameters from datastore endpoint '{}'",
                            endpoint.getName());
                        return transportParams;
                    }
                }
            }
        }
        return null;
    }

    private static @Nullable InetSocketAddress readBind(final DataBroker broker, final boolean ssh) {
        final var endpoints = readEndpoints(broker);
        if (endpoints == null) {
            return null;
        }

        for (var endpoint : endpoints.nonnullEndpoint().values()) {
            final var transport = endpoint.getTransport();
            final TcpServerGrouping tcp;
            if (ssh && transport instanceof Ssh sshCase) {
                final var sshParams = sshCase.getSsh();
                if (sshParams == null) {
                    LOG.warn("Ignoring Call Home SSH endpoint '{}' with no ssh parameters", endpoint.getName());
                    continue;
                }
                tcp = sshParams.getTcpServerParameters();
            } else if (!ssh && transport instanceof Tls tlsCase) {
                final var tlsParams = tlsCase.getTls();
                if (tlsParams == null) {
                    LOG.warn("Ignoring Call Home TLS endpoint '{}' with no tls parameters", endpoint.getName());
                    continue;
                }
                tcp = tlsParams.getTcpServerParameters();
            } else {
                if (!(transport instanceof Ssh || transport instanceof Tls)) {
                    // The 'transport' choice is mandatory and only models SSH and TLS, so this is unexpected.
                    LOG.warn("Ignoring Call Home endpoint '{}' with unsupported transport; expected SSH or TLS",
                        endpoint.getName());
                }
                // Otherwise the endpoint just uses the other transport; skip it while resolving this one.
                continue;
            }

            final var bind = tcp == null ? null : tcp.nonnullLocalBind().values().stream().findFirst()
                .orElse(null);
            if (bind == null || bind.getLocalPort() == null) {
                LOG.trace("Skipping Call Home endpoint '{}': no local-bind port configured", endpoint.getName());
                continue;
            }

            final var protocol = ssh ? "SSH" : "TLS";
            final var localAddress = bind.getLocalAddress();
            // A missing local-address binds to the wildcard address.
            final var address = localAddress == null ? null : IetfInetUtil.inetAddressFor(localAddress);
            final var port = bind.getLocalPort().getValue().toJava();
            final var bindAddress = new InetSocketAddress(address, port);
            LOG.info("Using Call Home {} listener bind from datastore endpoints", protocol);
            LOG.debug("Endpoint '{}' bind resolved to {}", endpoint.getName(), bindAddress);
            return bindAddress;
        }
        return null;
    }

    private static @Nullable Endpoints readEndpoints(final DataBroker broker) {
        try (var tx = broker.newReadOnlyTransaction()) {
            return tx.read(LogicalDatastoreType.CONFIGURATION, ENDPOINTS_II).get().orElse(null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while reading Call Home listen endpoints, falling back to static configuration", e);
            return null;
        } catch (ExecutionException e) {
            LOG.warn("Unable to read Call Home listen endpoints, falling back to static configuration", e);
            return null;
        }
    }
}
