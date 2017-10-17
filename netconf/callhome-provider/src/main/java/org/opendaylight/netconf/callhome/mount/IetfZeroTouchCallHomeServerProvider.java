/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.mount;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.callhome.protocol.CallHomeAuthorizationProvider;
import org.opendaylight.netconf.callhome.protocol.NetconfCallHomeServer;
import org.opendaylight.netconf.callhome.protocol.NetconfCallHomeServerBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.callhome.device.status.rev170112.Device1;
import org.opendaylight.yang.gen.v1.urn.opendaylight.callhome.device.status.rev170112.Device1Builder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.NetconfCallhomeServer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.netconf.callhome.server.AllowedDevices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.netconf.callhome.server.allowed.devices.Device;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.netconf.callhome.server.allowed.devices.DeviceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.netconf.callhome.server.allowed.devices.DeviceKey;
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

    public IetfZeroTouchCallHomeServerProvider(DataBroker dataBroker, CallHomeMountDispatcher mountDispacher) {
        this.dataBroker = dataBroker;
        this.mountDispacher = mountDispacher;
        this.authProvider = new CallHomeAuthProviderImpl(dataBroker);
        this.statusReporter = new CallhomeStatusReporter(dataBroker);
    }

    public void init() {
        // Register itself as a listener to changes in Devices subtree
        try {
            LOG.info("Initializing provider for {}", APPNAME);
            initializeServer();
            listenerReg = dataBroker.registerDataTreeChangeListener(
                    new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION, ALL_DEVICES), this);
            LOG.info("Initialization complete for {}", APPNAME);
        } catch (IOException | Configuration.ConfigurationException e) {
            LOG.error("Unable to successfully initialize", e);
        }
    }

    public void setPort(String portStr) {
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
        CallHomeAuthorizationProvider provider = this.getCallHomeAuthorization();
        NetconfCallHomeServerBuilder builder = new NetconfCallHomeServerBuilder(provider, mountDispacher,
                                                                                statusReporter);
        if (port > 0) {
            builder.setBindAddress(new InetSocketAddress(port));
        }
        server = builder.build();
        server.bind();
        mountDispacher.createTopology();
        LOG.info("Initialization complete for Call Home server instance");
    }

    @VisibleForTesting
    void assertValid(Object obj, String description) {
        if (obj == null) {
            throw new RuntimeException(
                    String.format("Failed to find %s in IetfZeroTouchCallHomeProvider.initialize()", description));
        }
    }

    @Override
    public void close() throws Exception {
        authProvider.close();
        statusReporter.close();

        // FIXME unbind the server
        if (this.listenerReg != null) {
            listenerReg.close();
        }
        if (server != null) {
            server.close();
        }

        LOG.info("Successfully closed provider for {}", APPNAME);
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<AllowedDevices>> changes) {
        // In case of any changes to the devices datatree, register the changed values with callhome server
        // As of now, no way to add a new callhome client key to the CallHomeAuthorization instance since
        // its created under CallHomeAuthorizationProvider.
        // Will have to redesign a bit here.
        // CallHomeAuthorization.
        ReadOnlyTransaction roConfigTx = dataBroker.newReadOnlyTransaction();
        ListenableFuture<Optional<AllowedDevices>> devicesFuture = roConfigTx
                .read(LogicalDatastoreType.CONFIGURATION, IetfZeroTouchCallHomeServerProvider.ALL_DEVICES);

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
            LOG.error("Error trying to read the whitelist devices: {}", e);
        }
    }

    private void handleDeletedDevices(Set<InstanceIdentifier<?>> deletedDevices) {
        if (deletedDevices.isEmpty()) {
            return;
        }

        ReadWriteTransaction opTx = dataBroker.newReadWriteTransaction();

        int numRemoved = deletedDevices.size();

        for (InstanceIdentifier<?> removedIID : deletedDevices) {
            LOG.info("Deleting the entry for callhome device {}", removedIID);
            opTx.delete(LogicalDatastoreType.OPERATIONAL, removedIID);
        }

        if (numRemoved > 0) {
            opTx.submit();
        }
    }

    private List<Device> getReadDevices(
            ListenableFuture<Optional<AllowedDevices>> devicesFuture) throws InterruptedException, ExecutionException {
        Optional<AllowedDevices> opt = devicesFuture.get();
        return opt.isPresent() ? opt.get().getDevice() : Collections.emptyList();
    }

    private void readAndUpdateStatus(Device cfgDevice) throws InterruptedException, ExecutionException {
        InstanceIdentifier<Device> deviceIID = InstanceIdentifier.create(NetconfCallhomeServer.class)
                .child(AllowedDevices.class).child(Device.class, new DeviceKey(cfgDevice.getUniqueId()));

        ReadWriteTransaction tx = dataBroker.newReadWriteTransaction();
        ListenableFuture<Optional<Device>> deviceFuture = tx.read(LogicalDatastoreType.OPERATIONAL, deviceIID);

        Optional<Device> opDevGet = deviceFuture.get();
        Device1 devStatus = new Device1Builder().setDeviceStatus(Device1.DeviceStatus.DISCONNECTED).build();
        if (opDevGet.isPresent()) {
            Device opDevice = opDevGet.get();
            devStatus = opDevice.getAugmentation(Device1.class);
        }

        cfgDevice = new DeviceBuilder().addAugmentation(Device1.class, devStatus)
                .setSshHostKey(cfgDevice.getSshHostKey()).setUniqueId(cfgDevice.getUniqueId()).build();

        tx.merge(LogicalDatastoreType.OPERATIONAL, deviceIID, cfgDevice);
        tx.submit();
    }
}
