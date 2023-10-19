/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import com.google.common.annotations.VisibleForTesting;
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
import org.opendaylight.netconf.callhome.protocol.CallHomeAuthorizationProvider;
import org.opendaylight.netconf.callhome.protocol.NetconfCallHomeServer;
import org.opendaylight.netconf.callhome.protocol.NetconfCallHomeServerBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.callhome.device.status.rev170112.Device1;
import org.opendaylight.yang.gen.v1.urn.opendaylight.callhome.device.status.rev170112.Device1Builder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev201015.NetconfCallhomeServer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev201015.netconf.callhome.server.AllowedDevices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev201015.netconf.callhome.server.allowed.devices.Device;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev201015.netconf.callhome.server.allowed.devices.DeviceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev201015.netconf.callhome.server.allowed.devices.DeviceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev201015.netconf.callhome.server.allowed.devices.device.Transport;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev201015.netconf.callhome.server.allowed.devices.device.transport.Ssh;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev201015.netconf.callhome.server.allowed.devices.device.transport.SshBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev201015.netconf.callhome.server.allowed.devices.device.transport.Tls;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev201015.netconf.callhome.server.allowed.devices.device.transport.ssh.SshClientParams;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev201015.netconf.callhome.server.allowed.devices.device.transport.ssh.SshClientParamsBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IetfZeroTouchCallHomeServerProvider implements AutoCloseable, DataTreeChangeListener<AllowedDevices> {
    private static final String APPNAME = "CallHomeServer";
    static final InstanceIdentifier<AllowedDevices> ALL_DEVICES = InstanceIdentifier.create(NetconfCallhomeServer.class)
            .child(AllowedDevices.class);

    private static final Logger LOG = LoggerFactory.getLogger(IetfZeroTouchCallHomeServerProvider.class);

    private final DataBroker dataBroker;
    private final CallHomeMountDispatcher mountDispacher;
    private final CallHomeAuthProviderImpl authProvider;

    protected NetconfCallHomeServer server;

    private ListenerRegistration<IetfZeroTouchCallHomeServerProvider> listenerReg = null;

    private static final String CALL_HOME_PORT_KEY = "DefaultCallHomePort";
    private int port = 0; // 0 = use default in NetconfCallHomeBuilder
    private final CallhomeStatusReporter statusReporter;

    public IetfZeroTouchCallHomeServerProvider(final DataBroker dataBroker,
            final CallHomeMountDispatcher mountDispacher) {
        this.dataBroker = dataBroker;
        this.mountDispacher = mountDispacher;
        authProvider = new CallHomeAuthProviderImpl(dataBroker);
        statusReporter = new CallhomeStatusReporter(dataBroker);
    }

    public void init() {
        // Register itself as a listener to changes in Devices subtree
        try {
            LOG.info("Initializing provider for {}", APPNAME);
            initializeServer();
            listenerReg = dataBroker.registerDataTreeChangeListener(
                DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION, ALL_DEVICES), this);
            LOG.info("Initialization complete for {}", APPNAME);
        } catch (IOException | Configuration.ConfigurationException e) {
            LOG.error("Unable to successfully initialize", e);
        }
    }

    public void setPort(final String portStr) {
        try {
            Configuration configuration = new Configuration();
            configuration.set(CALL_HOME_PORT_KEY, portStr);
            port = configuration.getAsPort(CALL_HOME_PORT_KEY);
            LOG.info("Setting port for call home server to {}", portStr);
        } catch (Configuration.ConfigurationException e) {
            LOG.error("Problem trying to set port for call home server {}", portStr, e);
        }
    }

    private CallHomeAuthorizationProvider getCallHomeAuthorization() {
        return new CallHomeAuthProviderImpl(dataBroker);
    }

    private void initializeServer() throws IOException {
        LOG.info("Initializing Call Home server instance");
        CallHomeAuthorizationProvider provider = getCallHomeAuthorization();
        NetconfCallHomeServerBuilder builder = new NetconfCallHomeServerBuilder(provider, mountDispacher,
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

    @VisibleForTesting
    void assertValid(final Object obj, final String description) {
        if (obj == null) {
            throw new IllegalStateException(
                "Failed to find " + description + " in IetfZeroTouchCallHomeProvider.initialize()");
        }
    }

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
            LOG.error("Error trying to read the allowlist devices", e);
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

        final Device1 devStatus;
        Optional<Device> opDevGet = deviceFuture.get();
        if (opDevGet.isPresent()) {
            devStatus = opDevGet.get().augmentation(Device1.class);
        } else {
            devStatus = new Device1Builder().setDeviceStatus(Device1.DeviceStatus.DISCONNECTED).build();
        }

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

    private Device createOperationalDevice(final Device cfgDevice, final Device1 devStatus) {
        final DeviceBuilder deviceBuilder = new DeviceBuilder()
            .addAugmentation(devStatus)
            .setUniqueId(cfgDevice.getUniqueId());
        if (cfgDevice.getTransport() instanceof Ssh) {
            final String hostKey = ((Ssh) cfgDevice.getTransport()).getSshClientParams().getHostKey();
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
