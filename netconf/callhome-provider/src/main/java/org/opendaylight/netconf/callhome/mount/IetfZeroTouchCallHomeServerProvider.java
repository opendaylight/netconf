/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.mount;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import java.io.IOException;
import java.io.File;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
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
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class IetfZeroTouchCallHomeServerProvider implements AutoCloseable, DataChangeListener
{
    private static final String APPNAME = "CallHomeServer";
    static final InstanceIdentifier<AllowedDevices> ALL_DEVICES = InstanceIdentifier.create(NetconfCallhomeServer.class).child(AllowedDevices.class);

    private static final Logger LOG = LoggerFactory.getLogger(IetfZeroTouchCallHomeServerProvider.class);

    private final DataBroker dataBroker;
    private final CallHomeMountDispatcher mountDispacher;
    private CallHomeAuthProviderImpl authProvider ;

    protected NetconfCallHomeServer server;

    private ListenerRegistration<IetfZeroTouchCallHomeServerProvider> listenerReg = null;

    private static final String CALL_HOME_PORT_KEY = "DefaultCallHomePort";
    private static String configurationPath = "etc"+File.pathSeparator+"ztp-callhome-config.cfg";
    private int port = 0; // 0 = use default in NetconfCallHomeBuilder
    private CallhomeStatusReporter statusReporter;

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
            loadConfigurableValues(configurationPath);
            initializeServer();
            dataBroker.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,ALL_DEVICES,this, AsyncDataBroker.DataChangeScope.SUBTREE);
            LOG.info( "Initialization complete for {}", APPNAME);
        }
        catch(IOException | Configuration.ConfigurationException e) {
            LOG.error("Unable to successfully initialize", e);
        }
    }

    private void loadConfigurableValues(String configurationPath)
            throws Configuration.ConfigurationException {
        try {
            Configuration configuration = new Configuration(configurationPath);
            port = configuration.getAsPort(CALL_HOME_PORT_KEY);
        }
        catch(Configuration.ConfigurationException e) {
            LOG.error("Problem trying to load configuration values from {}", configurationPath, e);
        }
    }

    private CallHomeAuthorizationProvider getCallHomeAuthorization() {
        return new CallHomeAuthProviderImpl(dataBroker);
    }

    private void initializeServer() throws IOException {
        LOG.info( "Initializing Call Home server instance");
        CallHomeAuthorizationProvider provider = this.getCallHomeAuthorization();
        NetconfCallHomeServerBuilder builder = new NetconfCallHomeServerBuilder(
                provider, mountDispacher, statusReporter);
        if (port > 0)
            builder.setBindAddress(new InetSocketAddress(port));
        server = builder.build();
        server.bind();
        mountDispacher.createTopology();
        LOG.info( "Initialization complete for Call Home server instance");
    }

    @VisibleForTesting
    void assertValid(Object obj, String description) {
        if (obj == null)
            throw new RuntimeException(String.format("Failed to find %s in IetfZeroTouchCallHomeProvider.initialize()", description));
    }

    @Override
    public void close() throws Exception {
        authProvider.close();
        statusReporter.close();

        // FIXME unbind the server
        if ( this.listenerReg != null ) {
            listenerReg.close();
        }
        if(server != null ) {
            server.close();
        }

        LOG.info("Successfully closed provider for {}", APPNAME);
    }

    @Override
    public void onDataChanged(AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change) {

        // In case of any changes to the devices datatree, register the changed values with callhome server
        // As of now, no way to add a new callhome client key to the CallHomeAuthorization instance since
        // its created under CallHomeAuthorizationProvider.
        // Will have to redesign a bit here.
        // CallHomeAuthorization.
        ReadOnlyTransaction roConfigTx = dataBroker.newReadOnlyTransaction();
        CheckedFuture<Optional<AllowedDevices>, ReadFailedException> devicesFuture =
                roConfigTx.read(LogicalDatastoreType.CONFIGURATION, IetfZeroTouchCallHomeServerProvider.ALL_DEVICES);

        if (hasDeletedDevices(change))
            handleDeletedDevices(change);

        try {
            for(Device confDevice : getReadDevices(devicesFuture)) {
                readAndUpdateStatus(confDevice);
            }
        }
        catch(ReadFailedException e) {
            LOG.error("Error trying to read the whitelist devices: {}", e);
        }
    }

    private boolean hasDeletedDevices(AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change) {
        return change.getRemovedPaths() != null;
    }

    private void handleDeletedDevices(AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change) {
        checkArgument(change.getRemovedPaths() != null);

        ReadWriteTransaction opTx = dataBroker.newReadWriteTransaction();

        Set<InstanceIdentifier<?>> removedDevices = change.getRemovedPaths();
        int numRemoved = removedDevices.size();

        Iterator<InstanceIdentifier<?>> iterator = removedDevices.iterator();
        while(iterator.hasNext()){
            InstanceIdentifier<?> removedIID = iterator.next();
            LOG.info("Deleting the entry for callhome device {}",removedIID);
            opTx.delete(LogicalDatastoreType.OPERATIONAL, removedIID);
        }

        if (numRemoved > 0)
            opTx.submit();
    }

    private List<Device> getReadDevices(CheckedFuture<Optional<AllowedDevices>, ReadFailedException> devicesFuture)
            throws ReadFailedException {
        Optional<AllowedDevices> opt = devicesFuture.checkedGet();
        if (opt.isPresent()) {
            AllowedDevices confDevices = opt.get();
            if (confDevices != null) {
                LOG.debug("Read {} devices", confDevices.getDevice().size());
                return confDevices.getDevice();
            }
        }

        LOG.debug("Failed to read devices");
        return new ArrayList<>();
    }

    private void readAndUpdateStatus(Device cfgDevice) throws ReadFailedException {
        InstanceIdentifier<Device> deviceIID  = InstanceIdentifier.create(NetconfCallhomeServer.class)
                .child(AllowedDevices.class)
                .child(Device.class, new DeviceKey(cfgDevice.getUniqueId()));

        ReadWriteTransaction tx = dataBroker.newReadWriteTransaction();
        CheckedFuture<Optional<Device>, ReadFailedException> deviceFuture = tx.read(LogicalDatastoreType.OPERATIONAL, deviceIID);

        Optional<Device> opDevGet = deviceFuture.checkedGet();
        Device1 devStatus = new Device1Builder().setDeviceStatus(Device1.DeviceStatus.DISCONNECTED).build();
        if(opDevGet.isPresent()) {
            Device opDevice = opDevGet.get();
            devStatus = opDevice.getAugmentation(Device1.class);
        }

        Device newOpDevice = new DeviceBuilder()
                .addAugmentation(Device1.class, devStatus)
                .setSshHostKey(cfgDevice.getSshHostKey())
                .setUniqueId(cfgDevice.getUniqueId()).build();

        cfgDevice = newOpDevice;

        tx.merge(LogicalDatastoreType.OPERATIONAL, deviceIID, cfgDevice);
        tx.submit();
    }
}
