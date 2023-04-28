/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.binding.api.ReadTransaction;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.callhome.protocol.AuthorizedKeysDecoder;
import org.opendaylight.netconf.callhome.protocol.StatusRecorder;
import org.opendaylight.netconf.topology.spi.NetconfNodeUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.NetconfCallhomeServer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.AllowedDevices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.Device;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.Device.DeviceStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.DeviceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.DeviceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.device.transport.Ssh;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.device.transport.SshBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.device.transport.ssh.SshClientParamsBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CallhomeStatusReporter implements DataTreeChangeListener<Node>, StatusRecorder, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(CallhomeStatusReporter.class);

    private final DataBroker dataBroker;
    private final ListenerRegistration<CallhomeStatusReporter> reg;

    CallhomeStatusReporter(final DataBroker broker) {
        dataBroker = broker;
        reg = dataBroker.registerDataTreeChangeListener(DataTreeIdentifier.create(LogicalDatastoreType.OPERATIONAL,
            NetconfNodeUtils.DEFAULT_TOPOLOGY_IID.child(Node.class)), this);
    }

    @Override
    public void onDataTreeChanged(final Collection<DataTreeModification<Node>> changes) {
        for (DataTreeModification<Node> change : changes) {
            final DataObjectModification<Node> rootNode = change.getRootNode();
            final InstanceIdentifier<Node> identifier = change.getRootPath().getRootIdentifier();
            switch (rootNode.getModificationType()) {
                case WRITE:
                case SUBTREE_MODIFIED:
                    if (isNetconfNode(rootNode.getDataAfter())) {
                        NodeId nodeId = getNodeId(identifier);
                        if (nodeId != null) {
                            NetconfNode nnode = rootNode.getDataAfter().augmentation(NetconfNode.class);
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

    private static boolean isNetconfNode(final Node node) {
        return node.augmentation(NetconfNode.class) != null;
    }

    private static NodeId getNodeId(final InstanceIdentifier<?> path) {
        NodeKey key = path.firstKeyOf(Node.class);
        return key != null ? key.getNodeId() : null;
    }

    private void handledNetconfNode(final NodeId nodeId, final NetconfNode nnode) {
        switch (nnode.getConnectionStatus()) {
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

    private void handleConnectedNetconfNode(final NodeId nodeId) {
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

    private void handleDisconnectedNetconfNode(final NodeId nodeId) {
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

    private void handleUnableToConnectNetconfNode(final NodeId nodeId) {
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

    void asForceListedDevice(final String id, final PublicKey serverKey) {
        NodeId nid = new NodeId(id);
        Device device = newDevice(id, serverKey, DeviceStatus.DISCONNECTED);
        writeDevice(nid, device);
    }

    void asUnlistedDevice(final String id, final PublicKey serverKey) {
        NodeId nid = new NodeId(id);
        Device device = newDevice(id, serverKey, DeviceStatus.FAILEDNOTALLOWED);
        writeDevice(nid, device);
    }

    private static Device newDevice(final String id, final PublicKey serverKey, final DeviceStatus status) {
        // used only for netconf devices that are connected via SSH transport and global credentials
        String sshEncodedKey = serverKey.toString();
        try {
            sshEncodedKey = AuthorizedKeysDecoder.encodePublicKey(serverKey);
        } catch (IOException e) {
            LOG.warn("Unable to encode public key to ssh format.", e);
        }
        return new DeviceBuilder()
            .setUniqueId(id)
            .withKey(new DeviceKey(id))
            .setTransport(new SshBuilder()
                .setSshClientParams(new SshClientParamsBuilder().setHostKey(sshEncodedKey).build())
                .build())
            .setDeviceStatus(status)
            .build();
    }

    private Device readAndGetDevice(final NodeId nodeId) {
        return readDevice(nodeId).orElse(null);
    }

    private Optional<Device> readDevice(final NodeId nodeId) {
        try (ReadTransaction opTx = dataBroker.newReadOnlyTransaction()) {
            InstanceIdentifier<Device> deviceIID = buildDeviceInstanceIdentifier(nodeId);
            return opTx.read(LogicalDatastoreType.OPERATIONAL, deviceIID).get();
        } catch (InterruptedException | ExecutionException e) {
            return Optional.empty();
        }
    }

    private void writeDevice(final NodeId nodeId, final Device modifiedDevice) {
        WriteTransaction opTx = dataBroker.newWriteOnlyTransaction();
        opTx.merge(LogicalDatastoreType.OPERATIONAL, buildDeviceInstanceIdentifier(nodeId), modifiedDevice);
        commit(opTx, modifiedDevice.key());
    }

    private static InstanceIdentifier<Device> buildDeviceInstanceIdentifier(final NodeId nodeId) {
        return InstanceIdentifier.create(NetconfCallhomeServer.class)
            .child(AllowedDevices.class)
            .child(Device.class, new DeviceKey(nodeId.getValue()));
    }

    private static Device withConnectedStatus(final Device opDev) {
        return deviceWithStatus(opDev, DeviceStatus.CONNECTED);
    }

    private static Device withFailedStatus(final Device opDev) {
        return deviceWithStatus(opDev, DeviceStatus.FAILED);
    }

    private static Device withDisconnectedStatus(final Device opDev) {
        return deviceWithStatus(opDev, DeviceStatus.DISCONNECTED);
    }

    private static Device withFailedAuthStatus(final Device opDev) {
        return deviceWithStatus(opDev, DeviceStatus.FAILEDAUTHFAILURE);
    }

    private static Device deviceWithStatus(final Device opDev, final DeviceStatus status) {
        final DeviceBuilder deviceBuilder = new DeviceBuilder()
            .setUniqueId(opDev.getUniqueId())
            .setDeviceStatus(status);
        if (opDev.getTransport() != null) {
            deviceBuilder.setTransport(opDev.getTransport());
        } else {
            deviceBuilder.setSshHostKey(opDev.getSshHostKey());
        }
        return deviceBuilder.build();
    }

    private void setDeviceStatus(final Device device) {
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        InstanceIdentifier<Device> deviceIId = InstanceIdentifier.create(NetconfCallhomeServer.class)
                        .child(AllowedDevices.class)
                        .child(Device.class, device.key());

        tx.merge(LogicalDatastoreType.OPERATIONAL, deviceIId, device);
        commit(tx, device.key());
    }

    private static void commit(final WriteTransaction tx, final DeviceKey device) {
        tx.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("Device {} committed", device);
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.warn("Failed to commit device {}", device, cause);
            }
        }, MoreExecutors.directExecutor());
    }

    private AllowedDevices getDevices() {
        try (ReadTransaction rxTransaction = dataBroker.newReadOnlyTransaction()) {
            return rxTransaction.read(LogicalDatastoreType.OPERATIONAL, IetfZeroTouchCallHomeServerProvider.ALL_DEVICES)
                    .get().orElse(null);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("Error trying to read the whitelist devices", e);
            return null;
        }
    }

    private Collection<Device> getDevicesAsList() {
        AllowedDevices devices = getDevices();
        return devices == null ? Collections.emptyList() : devices.nonnullDevice().values();
    }

    @Override
    public void reportFailedAuth(final PublicKey sshKey) {
        AuthorizedKeysDecoder decoder = new AuthorizedKeysDecoder();

        for (final Device device : getDevicesAsList()) {
            final String keyString;
            if (device.getTransport() instanceof Ssh ssh) {
                keyString = ssh.getSshClientParams().getHostKey();
            } else {
                keyString = device.getSshHostKey();
            }
            if (keyString == null) {
                LOG.info("Whitelist device {} does not have a host key, skipping it", device.getUniqueId());
                continue;
            }

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
            } catch (GeneralSecurityException e) {
                LOG.error("Failed decoding a device key with host key: {}", keyString, e);
                return;
            }
        }

        LOG.error("No match found for the failed auth device (should have been filtered by whitelist). Key: {}",
                sshKey);
    }

    @Override
    public void close() {
        reg.close();
    }
}
