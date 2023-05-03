/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.channel.nio.NioEventLoopGroup;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.binding.api.ReadTransaction;
import org.opendaylight.mdsal.binding.api.ReadWriteTransaction;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.callhome.protocol.NetconfCallHomeServer;
import org.opendaylight.netconf.callhome.protocol.NetconfCallHomeServerBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.NetconfCallhomeServer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.AllowedDevices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.Device;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.Device.DeviceStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.DeviceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.DeviceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.device.Transport;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.device.transport.Ssh;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.device.transport.SshBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.device.transport.Tls;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.device.transport.ssh.SshClientParams;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.device.transport.ssh.SshClientParamsBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = { })
public final class IetfZeroTouchCallHomeServerProvider
        implements AutoCloseable, DataTreeChangeListener<AllowedDevices> {
    private static final String APPNAME = "CallHomeServer";
    static final InstanceIdentifier<AllowedDevices> ALL_DEVICES = InstanceIdentifier.create(NetconfCallhomeServer.class)
            .child(AllowedDevices.class);

    private static final Logger LOG = LoggerFactory.getLogger(IetfZeroTouchCallHomeServerProvider.class);

    private final DataBroker dataBroker;
    private final CallHomeMountDispatcher mountDispacher;
    private final CallHomeAuthProviderImpl authProvider;
    private final CallhomeStatusReporter statusReporter;
    private final int port;

    private NetconfCallHomeServer server;
    private ListenerRegistration<IetfZeroTouchCallHomeServerProvider> listenerReg = null;

    @Activate
    public IetfZeroTouchCallHomeServerProvider(@Reference final DataBroker dataBroker,
            @Reference final CallHomeMountDispatcher mountDispacher) {
        // FIXME: make this configurable
        this(dataBroker, mountDispacher, Uint16.valueOf(4334));
    }

    public IetfZeroTouchCallHomeServerProvider(final DataBroker dataBroker,
            final CallHomeMountDispatcher mountDispacher, final Uint16 port) {
        this.dataBroker = requireNonNull(dataBroker);
        this.mountDispacher = requireNonNull(mountDispacher);

        LOG.info("Setting port for call home server to {}", port);
        this.port = port.toJava();

        // FIXME: these should be separate components
        authProvider = new CallHomeAuthProviderImpl(dataBroker);
        statusReporter = new CallhomeStatusReporter(dataBroker);

        LOG.info("Initializing provider for {}", APPNAME);

        // Register itself as a listener to changes in Devices subtree
        try {
            initializeServer();
        } catch (IOException | Configuration.ConfigurationException e) {
            LOG.error("Unable to successfully initialize", e);
            return;
        }

        listenerReg = dataBroker.registerDataTreeChangeListener(
            DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION, ALL_DEVICES), this);
        LOG.info("Initialization complete for {}", APPNAME);
    }

    private void initializeServer() throws IOException {
        LOG.info("Initializing Call Home server instance");
        NetconfCallHomeServerBuilder builder = new NetconfCallHomeServerBuilder(authProvider, mountDispacher,
            statusReporter);
        if (port > 0) {
            builder.setBindAddress(new InetSocketAddress(port));
        }
        builder.setNettyGroup(new NioEventLoopGroup());
        server = builder.build();
        server.bind();
        mountDispacher.createTopology();
        LOG.info("Initialization complete for Call Home server instance");
    }

    @Deactivate
    @Override
    public void close() {
        authProvider.close();
        statusReporter.close();

        // FIXME unbind the server
        if (listenerReg != null) {
            listenerReg.close();
        }
        if (server != null) {
            server.close();
        }

        LOG.info("Successfully closed provider for {}", APPNAME);
    }

    @Override
    public void onDataTreeChanged(final Collection<DataTreeModification<AllowedDevices>> changes) {
        // In case of any changes to the devices datatree, register the changed values with callhome server
        // As of now, no way to add a new callhome client key to the CallHomeAuthorization instance since
        // its created under CallHomeAuthorizationProvider.
        // Will have to redesign a bit here.
        // CallHomeAuthorization.
        final ListenableFuture<Optional<AllowedDevices>> devicesFuture;
        try (ReadTransaction roConfigTx = dataBroker.newReadOnlyTransaction()) {
            devicesFuture = roConfigTx.read(LogicalDatastoreType.CONFIGURATION,
                IetfZeroTouchCallHomeServerProvider.ALL_DEVICES);
        }

        Set<InstanceIdentifier<?>> deletedDevices = new HashSet<>();
        for (DataTreeModification<AllowedDevices> change : changes) {
            DataObjectModification<AllowedDevices> rootNode = change.getRootNode();
            switch (rootNode.getModificationType()) {
                case DELETE:
                    deletedDevices.add(change.getRootPath().getRootIdentifier());
                    break;
                default:
                    break;
            }
        }

        handleDeletedDevices(deletedDevices);

        try {
            for (Device confDevice : getReadDevices(devicesFuture)) {
                readAndUpdateStatus(confDevice);
            }
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("Error trying to read the whitelist devices", e);
        }
    }

    private void handleDeletedDevices(final Set<InstanceIdentifier<?>> deletedDevices) {
        if (deletedDevices.isEmpty()) {
            return;
        }

        WriteTransaction opTx = dataBroker.newWriteOnlyTransaction();

        for (InstanceIdentifier<?> removedIID : deletedDevices) {
            LOG.info("Deleting the entry for callhome device {}", removedIID);
            opTx.delete(LogicalDatastoreType.OPERATIONAL, removedIID);
        }

        opTx.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("Device deletions committed");
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.warn("Failed to commit device deletions", cause);
            }
        }, MoreExecutors.directExecutor());
    }

    private static Collection<Device> getReadDevices(final ListenableFuture<Optional<AllowedDevices>> devicesFuture)
            throws InterruptedException, ExecutionException {
        return devicesFuture.get().map(AllowedDevices::nonnullDevice).orElse(Map.of()).values();
    }

    private void readAndUpdateStatus(final Device cfgDevice) throws InterruptedException, ExecutionException {
        InstanceIdentifier<Device> deviceIID = InstanceIdentifier.create(NetconfCallhomeServer.class)
                .child(AllowedDevices.class).child(Device.class, new DeviceKey(cfgDevice.getUniqueId()));

        ReadWriteTransaction tx = dataBroker.newReadWriteTransaction();
        ListenableFuture<Optional<Device>> deviceFuture = tx.read(LogicalDatastoreType.OPERATIONAL, deviceIID);

        final DeviceStatus devStatus = deviceFuture.get()
            .map(Device::getDeviceStatus)
            .orElse(DeviceStatus.DISCONNECTED);

        final Device opDevice = createOperationalDevice(cfgDevice, devStatus);
        tx.merge(LogicalDatastoreType.OPERATIONAL, deviceIID, opDevice);
        tx.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("Device {} status update committed", cfgDevice.key());
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.warn("Failed to commit device {} status update", cfgDevice.key(), cause);
            }
        }, MoreExecutors.directExecutor());
    }

    private static Device createOperationalDevice(final Device cfgDevice, final DeviceStatus devStatus) {
        final DeviceBuilder deviceBuilder = new DeviceBuilder()
            .setUniqueId(cfgDevice.getUniqueId())
            .setDeviceStatus(devStatus);
        if (cfgDevice.getTransport() instanceof Ssh ssh) {
            final String hostKey = ssh.getSshClientParams().getHostKey();
            final SshClientParams params = new SshClientParamsBuilder().setHostKey(hostKey).build();
            final Transport sshTransport = new SshBuilder().setSshClientParams(params).build();
            deviceBuilder.setTransport(sshTransport);
        } else if (cfgDevice.getTransport() instanceof Tls) {
            deviceBuilder.setTransport(cfgDevice.getTransport());
        } else if (cfgDevice.getSshHostKey() != null) {
            deviceBuilder.setSshHostKey(cfgDevice.getSshHostKey());
        }
        return deviceBuilder.build();
    }
}
