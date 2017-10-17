/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.mount;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.callhome.protocol.AuthorizedKeysDecoder;
import org.opendaylight.netconf.callhome.protocol.StatusRecorder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.callhome.device.status.rev170112.Device1;
import org.opendaylight.yang.gen.v1.urn.opendaylight.callhome.device.status.rev170112.Device1Builder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.network.topology.topology.topology.types.TopologyNetconf;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.NetconfCallhomeServer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.netconf.callhome.server.AllowedDevices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.netconf.callhome.server.allowed.devices.Device;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.netconf.callhome.server.allowed.devices.DeviceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.netconf.callhome.server.allowed.devices.DeviceKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CallhomeStatusReporter implements DataTreeChangeListener<Node>, StatusRecorder, AutoCloseable {
    private static final InstanceIdentifier<Topology> NETCONF_TOPO_IID =
            InstanceIdentifier.create(NetworkTopology.class).child(Topology.class,
                    new TopologyKey(new TopologyId(TopologyNetconf.QNAME.getLocalName())));

    private static final Logger LOG = LoggerFactory.getLogger(CallhomeStatusReporter.class);

    private final DataBroker dataBroker;
    private final ListenerRegistration<CallhomeStatusReporter> reg;

    CallhomeStatusReporter(DataBroker broker) {
        this.dataBroker = broker;
        this.reg = dataBroker.registerDataTreeChangeListener(new DataTreeIdentifier<>(LogicalDatastoreType.OPERATIONAL,
            NETCONF_TOPO_IID.child(Node.class)), this);
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<Node>> changes) {
        for (DataTreeModification<Node> change: changes) {
            final DataObjectModification<Node> rootNode = change.getRootNode();
            final InstanceIdentifier<Node> identifier = change.getRootPath().getRootIdentifier();
            switch (rootNode.getModificationType()) {
                case WRITE:
                case SUBTREE_MODIFIED:
                    if (isNetconfNode(rootNode.getDataAfter())) {
                        NodeId nodeId = getNodeId(identifier);
                        if (nodeId != null) {
                            NetconfNode nnode = rootNode.getDataAfter().getAugmentation(NetconfNode.class);
                            handledNetconfNode(nodeId, nnode);
                        }
                    }
                    break;
                case DELETE:
                    if (isNetconfNode(rootNode.getDataBefore())) {
                        final NodeId nodeId = getNodeId(identifier);
                        if (nodeId != null) {
                            handleDisconnectedNetconfNode(nodeId);
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private boolean isNetconfNode(Node node) {
        return node.getAugmentation(NetconfNode.class) != null;
    }

    private NodeId getNodeId(final InstanceIdentifier<?> path) {
        NodeKey key = path.firstKeyOf(Node.class);
        return key != null ? key.getNodeId() : null;
    }

    private void handledNetconfNode(NodeId nodeId, NetconfNode nnode) {
        NetconfNodeConnectionStatus.ConnectionStatus csts = nnode.getConnectionStatus();

        switch (csts) {
            case Connected: {
                handleConnectedNetconfNode(nodeId);
                break;
            }
            default:
            case UnableToConnect: {
                handleUnableToConnectNetconfNode(nodeId);
                break;
            }
        }
    }

    private void handleConnectedNetconfNode(NodeId nodeId) {
        // Fully connected, all services for remote device are
        // available from the MountPointService.
        LOG.debug("NETCONF Node: {} is fully connected", nodeId.getValue());

        Device opDev = readAndGetDevice(nodeId);
        if (opDev == null) {
            LOG.warn("No corresponding callhome device found - exiting.");
        } else {
            Device modifiedDevice = withConnectedStatus(opDev);
            if (modifiedDevice == null) {
                return;
            }
            LOG.info("Setting successful status for callhome device id:{}.", nodeId);
            writeDevice(nodeId, modifiedDevice);
        }
    }

    private void handleDisconnectedNetconfNode(NodeId nodeId) {
        LOG.debug("NETCONF Node: {} disconnected", nodeId.getValue());

        Device opDev = readAndGetDevice(nodeId);
        if (opDev == null) {
            LOG.warn("No corresponding callhome device found - exiting.");
        } else {
            Device modifiedDevice = withDisconnectedStatus(opDev);
            if (modifiedDevice == null) {
                return;
            }
            LOG.info("Setting disconnected status for callhome device id:{}.", nodeId);
            writeDevice(nodeId, modifiedDevice);
        }
    }

    private void handleUnableToConnectNetconfNode(NodeId nodeId) {
        // The maximum configured number of reconnect attempts
        // have been reached. No more reconnects will be
        // attempted by the Netconf Connector.
        LOG.debug("NETCONF Node: {} connection failed", nodeId.getValue());

        Device opDev = readAndGetDevice(nodeId);
        if (opDev == null) {
            LOG.warn("No corresponding callhome device found - exiting.");
        } else {
            Device modifiedDevice = withFailedStatus(opDev);
            if (modifiedDevice == null) {
                return;
            }
            LOG.info("Setting failed status for callhome device id:{}.", nodeId);
            writeDevice(nodeId, modifiedDevice);
        }
    }

    void asForceListedDevice(String id, PublicKey serverKey) {
        NodeId nid = new NodeId(id);
        Device device = newDevice(id, serverKey, Device1.DeviceStatus.DISCONNECTED);
        writeDevice(nid, device);
    }

    void asUnlistedDevice(String id, PublicKey serverKey) {
        NodeId nid = new NodeId(id);
        Device device = newDevice(id, serverKey, Device1.DeviceStatus.FAILEDNOTALLOWED);
        writeDevice(nid, device);
    }

    private Device newDevice(String id, PublicKey serverKey, Device1.DeviceStatus status) {
        String sshEncodedKey = serverKey.toString();
        try {
            sshEncodedKey = AuthorizedKeysDecoder.encodePublicKey(serverKey);
        } catch (IOException e) {
            LOG.warn("Unable to encode public key to ssh format.", e);
        }
        Device1 d1 = new Device1Builder().setDeviceStatus(Device1.DeviceStatus.FAILEDNOTALLOWED).build();
        DeviceBuilder builder = new DeviceBuilder()
                .setUniqueId(id)
                .setKey(new DeviceKey(id))
                .setSshHostKey(sshEncodedKey)
                .addAugmentation(Device1.class, d1);

        return builder.build();
    }

    private Device readAndGetDevice(NodeId nodeId) {
        return readDevice(nodeId).orNull();
    }

    @Nonnull
    private Optional<Device> readDevice(NodeId nodeId) {
        ReadOnlyTransaction opTx = dataBroker.newReadOnlyTransaction();

        InstanceIdentifier<Device> deviceIID = buildDeviceInstanceIdentifier(nodeId);
        ListenableFuture<Optional<Device>> devFuture = opTx.read(LogicalDatastoreType.OPERATIONAL, deviceIID);
        try {
            return devFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            return Optional.absent();
        }
    }

    private void writeDevice(NodeId nodeId, Device modifiedDevice) {
        ReadWriteTransaction opTx = dataBroker.newReadWriteTransaction();
        opTx.merge(LogicalDatastoreType.OPERATIONAL, buildDeviceInstanceIdentifier(nodeId), modifiedDevice);
        opTx.submit();
    }

    private InstanceIdentifier<Device> buildDeviceInstanceIdentifier(NodeId nodeId) {
        return InstanceIdentifier.create(NetconfCallhomeServer.class)
                .child(AllowedDevices.class)
                .child(Device.class, new DeviceKey(nodeId.getValue()));
    }

    private Device withConnectedStatus(Device opDev) {
        Device1 status = new Device1Builder().setDeviceStatus(Device1.DeviceStatus.CONNECTED).build();
        return new DeviceBuilder().addAugmentation(Device1.class, status).setUniqueId(opDev.getUniqueId())
                .setSshHostKey(opDev.getSshHostKey()).build();
    }

    private Device withFailedStatus(Device opDev) {
        Device1 status = new Device1Builder().setDeviceStatus(Device1.DeviceStatus.FAILED).build();
        return new DeviceBuilder().addAugmentation(Device1.class, status).setUniqueId(opDev.getUniqueId())
                .setSshHostKey(opDev.getSshHostKey()).build();
    }

    private Device withDisconnectedStatus(Device opDev) {
        Device1 status = new Device1Builder().setDeviceStatus(Device1.DeviceStatus.DISCONNECTED).build();
        return new DeviceBuilder().addAugmentation(Device1.class, status).setUniqueId(opDev.getUniqueId())
                .setSshHostKey(opDev.getSshHostKey()).build();
    }

    private Device withFailedAuthStatus(Device opDev) {
        Device1 status = new Device1Builder().setDeviceStatus(Device1.DeviceStatus.FAILEDAUTHFAILURE).build();
        return new DeviceBuilder().addAugmentation(Device1.class, status).setUniqueId(opDev.getUniqueId())
                .setSshHostKey(opDev.getSshHostKey()).build();
    }

    private void setDeviceStatus(Device device) {
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        InstanceIdentifier<Device> deviceIId =
                InstanceIdentifier.create(NetconfCallhomeServer.class)
                        .child(AllowedDevices.class)
                        .child(Device.class, device.getKey());

        tx.merge(LogicalDatastoreType.OPERATIONAL, deviceIId, device);
        tx.submit();
    }

    private AllowedDevices getDevices() {
        ReadOnlyTransaction rxTransaction = dataBroker.newReadOnlyTransaction();
        ListenableFuture<Optional<AllowedDevices>> devicesFuture =
                rxTransaction.read(LogicalDatastoreType.OPERATIONAL, IetfZeroTouchCallHomeServerProvider.ALL_DEVICES);
        try {
            return devicesFuture.get().orNull();
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("Error trying to read the whitelist devices: {}", e);
            return null;
        }
    }

    private List<Device> getDevicesAsList() {
        AllowedDevices devices = getDevices();
        return devices == null ? new ArrayList<>() : devices.getDevice();
    }

    @Override
    public void reportFailedAuth(PublicKey sshKey) {
        AuthorizedKeysDecoder decoder = new AuthorizedKeysDecoder();

        for (Device device : getDevicesAsList()) {
            String keyString = device.getSshHostKey();

            try {
                PublicKey pubKey = decoder.decodePublicKey(keyString);
                if (sshKey.getAlgorithm().equals(pubKey.getAlgorithm()) && sshKey.equals(pubKey)) {
                    Device failedDevice = withFailedAuthStatus(device);
                    if (failedDevice == null) {
                        return;
                    }
                    LOG.info("Setting auth failed status for callhome device id:{}.", failedDevice.getUniqueId());
                    setDeviceStatus(failedDevice);
                    return;
                }
            } catch (InvalidKeySpecException | NoSuchAlgorithmException | NoSuchProviderException e) {
                LOG.error("Failed decoding a device key with host key: {} {}", keyString, e);
                return;
            }
        }

        LOG.error("No match found for the failed auth device (should have been filtered by whitelist). Key: {}",
                sshKey);
    }

    @Override
    public void close() throws Exception {
        reg.close();
    }
}
