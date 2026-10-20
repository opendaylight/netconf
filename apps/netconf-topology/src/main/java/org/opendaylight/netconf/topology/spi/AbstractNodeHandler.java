/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static java.util.Objects.requireNonNull;

import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.client.mdsal.LibraryModulesSchemas;
import org.opendaylight.netconf.client.mdsal.LibrarySchemaSourceProvider;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceBuilder;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceSchema;
import org.opendaylight.netconf.client.mdsal.SchemalessNetconfDevice;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.DeviceNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.NegotiatedSshAlg;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDevice;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.api.RpcsTimeoutAndRecoveryHandler;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.client.mdsal.spi.KeepaliveSalFacade;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.netconf.transport.ssh.SSHNegotiatedAlgListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev221225.NetconfNodeAugmentedOptional;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251205.netconf.node.augment.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.concepts.AbstractRegistration;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.model.api.source.YangTextSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common base of handlers holding all state associated with a NETCONF topology node. It builds the device-side
 * wiring of the node, i.e. the {@link NetconfDeviceCommunicator}, the NETCONF device with its schema setup and the
 * keepalive and RPC timeout facades, and forwards device state to a delegate {@link RemoteDeviceHandler}.
 *
 * <p>How the NETCONF session is established is left to subclasses: {@link NetconfNodeHandler} connects to the device
 * and reconnects as needed, {@link CallHomeNodeHandler} attaches to a transport accepted from a call-home device.
 */
abstract sealed class AbstractNodeHandler extends AbstractRegistration implements RemoteDeviceHandler
        permits CallHomeNodeHandler, NetconfNodeHandler {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractNodeHandler.class);

    private final @NonNull List<Registration> yanglibRegistrations;
    private final @NonNull NetconfNode node;
    private final @NonNull NodeId nodeId;
    private final @NonNull NetconfDeviceCommunicator communicator;
    private final @NonNull RemoteDeviceHandler delegate;
    private final @NonNull RemoteDeviceId deviceId;

    // SSH algorithms negotiated for the current session, reported to the delegate in onDeviceConnected(). Maintained
    // by subclasses: NetconfNodeHandler resets it on each connection attempt and fills it from its
    // SSHNegotiatedAlgListener, CallHomeNodeHandler sets it once from the accepted transport.
    @GuardedBy("this")
    private @Nullable NegotiatedSshAlg negotiatedSshAlg;

    AbstractNodeHandler(final NetconfTimer timer,
            final BaseNetconfSchemaProvider baseSchemaProvider, final SchemaResourceManager schemaManager,
            final NetconfTopologySchemaAssembler schemaAssembler,
            final DeviceActionFactory deviceActionFactory, final RemoteDeviceHandler delegate,
            final RemoteDeviceId deviceId, final NodeId nodeId, final NetconfNode node,
            final NetconfNodeAugmentedOptional nodeOptional) {
        this.delegate = requireNonNull(delegate);
        this.deviceId = requireNonNull(deviceId);
        this.node = requireNonNull(node);
        this.nodeId = requireNonNull(nodeId);

        // Setup reconnection on empty context, if so configured
        // FIXME: NETCONF-925: implement this
        if (nodeOptional != null && nodeOptional.getIgnoreMissingSchemaSources().getAllowed()) {
            LOG.warn("Ignoring missing schema sources is not currently implemented for {}", deviceId);
        }

        // The facade we are going it present to NetconfDevice
        RemoteDeviceHandler salFacade;
        final KeepaliveSalFacade keepAliveFacade;
        final long keepaliveDelay = node.requireKeepaliveDelay().toJava();
        if (keepaliveDelay > 0) {
            LOG.info("Adding keepalive facade, for device {}", nodeId);
            salFacade = keepAliveFacade = new KeepaliveSalFacade(deviceId, this, timer, keepaliveDelay);
        } else {
            salFacade = this;
            keepAliveFacade = null;
        }

        final var defaultRequestTimeout = node.requireDefaultRequestTimeoutMillis().toJava();
        final var rpcsDecorator = new RpcsTimeoutAndRecoveryHandler(deviceId, timer, defaultRequestTimeout);
        final RemoteDevice<NetconfDeviceCommunicator> device;
        if (node.requireSchemaless()) {
            device = new SchemalessNetconfDevice(baseSchemaProvider, deviceId, salFacade);
            yanglibRegistrations = List.of();
        } else {
            final var resources = schemaManager.getSchemaResources(node.getSchemaCacheDirectory(), nodeId.getValue());
            device = new NetconfDeviceBuilder()
                .setReconnectOnSchemasChange(node.requireReconnectOnChangedSchema())
                .setBaseSchemaProvider(baseSchemaProvider)
                .setDeviceSchemaProvider(resources)
                .setProcessingExecutor(schemaAssembler.executor())
                .setId(deviceId)
                .setSalFacade(salFacade)
                .setRpcsDecorator(rpcsDecorator)
                .setDeviceActionFactory(deviceActionFactory)
                .build();
            yanglibRegistrations = registerDeviceSchemaSources(deviceId, node, resources);
        }

        final int rpcMessageLimit = node.requireConcurrentRpcLimit().toJava();
        if (rpcMessageLimit < 1) {
            LOG.info("Concurrent rpc limit is smaller than 1, no limit will be enforced for device {}", deviceId);
        }

        communicator = new NetconfDeviceCommunicator(deviceId, device, rpcMessageLimit,
            NetconfNodeUtils.extractUserCapabilities(node));
        rpcsDecorator.setListener(communicator);
        if (keepAliveFacade != null) {
            keepAliveFacade.setListener(communicator);
        }
    }

    /**
     * Start establishing the NETCONF session with the device. State changes are reported to the delegate.
     */
    public abstract void connect();

    /**
     * Stop establishing or tear down the NETCONF session. Invoked once while this handler is being closed, before the
     * communicator and the delegate are closed.
     */
    abstract void closeSession();

    @NonNull NetconfNode netconfNode() {
        return node;
    }

    @NonNull NodeId nodeId() {
        return nodeId;
    }

    @NonNull NetconfDeviceCommunicator communicator() {
        return communicator;
    }

    @NonNull RemoteDeviceHandler delegate() {
        return delegate;
    }

    @NonNull RemoteDeviceId deviceId() {
        return deviceId;
    }

    final synchronized void setNegotiatedSshAlg(final @Nullable NegotiatedSshAlg sshAlg) {
        negotiatedSshAlg = sshAlg;
    }

    @Override
    protected synchronized void removeRegistration() {
        closeSession();

        communicator.close();
        delegate.close();
        yanglibRegistrations.forEach(Registration::close);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The {@code ignoredSshAlg} argument is disregarded: this handler tracks the algorithms negotiated for the
     * current session itself (via the {@link SSHNegotiatedAlgListener} of {@link NetconfNodeHandler}, or as provided
     * by the call-home transport to {@link CallHomeNodeHandler}) and substitutes that value when forwarding to its
     * delegate.
     */
    @Override
    public void onDeviceConnected(final NetconfDeviceSchema deviceSchema,
            final NetconfSessionPreferences sessionPreferences, final RemoteDeviceServices services,
            final @Nullable NegotiatedSshAlg ignoredSshAlg) {
        final NegotiatedSshAlg sshAlg;
        synchronized (this) {
            sshAlg = negotiatedSshAlg;
        }
        delegate.onDeviceConnected(deviceSchema, sessionPreferences, services, sshAlg);
    }

    @Override
    public void onNotification(final DOMNotification domNotification) {
        delegate.onNotification(domNotification);
    }

    private static List<Registration> registerDeviceSchemaSources(final RemoteDeviceId remoteDeviceId,
            final NetconfNode node, final DeviceNetconfSchemaProvider resources) {
        final var yangLibrary = node.getYangLibrary();
        if (yangLibrary != null) {
            final Uri uri = yangLibrary.getYangLibraryUrl();
            if (uri != null) {
                final var registrations = new ArrayList<Registration>();
                final var yangLibURL = uri.getValue();

                // pre register yang library sources as fallback schemas to schema registry
                final var yangLibUsername = yangLibrary.getUsername();
                final var yangLigPassword = yangLibrary.getPassword();
                final var schemas = yangLibUsername != null && yangLigPassword != null
                    ? LibraryModulesSchemas.create(yangLibURL, yangLibUsername, yangLigPassword)
                    : LibraryModulesSchemas.create(yangLibURL);

                final var registry = resources.registry();
                for (var entry : schemas.getAvailableModels().entrySet()) {
                    registrations.add(registry.registerSchemaSource(new LibrarySchemaSourceProvider(
                            schemas.getAvailableModels()),
                        PotentialSchemaSource.create(entry.getKey(), YangTextSource.class,
                            PotentialSchemaSource.Costs.REMOTE_IO.getValue())));
                }
                return List.copyOf(registrations);
            }
        }

        return List.of();
    }
}
