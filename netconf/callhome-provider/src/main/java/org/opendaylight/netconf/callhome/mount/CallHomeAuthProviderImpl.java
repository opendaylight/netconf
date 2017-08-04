/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import com.google.common.base.Objects;
import com.google.common.net.InetAddresses;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.callhome.protocol.AuthorizedKeysDecoder;
import org.opendaylight.netconf.callhome.protocol.CallHomeAuthorization;
import org.opendaylight.netconf.callhome.protocol.CallHomeAuthorization.Builder;
import org.opendaylight.netconf.callhome.protocol.CallHomeAuthorizationProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.NetconfCallhomeServer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.netconf.callhome.server.AllowedDevices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.netconf.callhome.server.Global;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.netconf.callhome.server.allowed.devices.Device;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CallHomeAuthProviderImpl implements CallHomeAuthorizationProvider, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(CallHomeAuthProviderImpl.class);
    private static final InstanceIdentifier<Global> GLOBAL_PATH =
            InstanceIdentifier.create(NetconfCallhomeServer.class).child(Global.class);
    private static final DataTreeIdentifier<Global> GLOBAL =
            new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION, GLOBAL_PATH);

    private static final InstanceIdentifier<Device> ALLOWED_DEVICES_PATH =
            InstanceIdentifier.create(NetconfCallhomeServer.class).child(AllowedDevices.class).child(Device.class);
    private static final DataTreeIdentifier<Device> ALLOWED_DEVICES =
            new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION, ALLOWED_DEVICES_PATH);
    private static final DataTreeIdentifier<Device> ALLOWED_OP_DEVICES =
            new DataTreeIdentifier<>(LogicalDatastoreType.OPERATIONAL, ALLOWED_DEVICES_PATH);

    private final GlobalConfig globalConfig = new GlobalConfig();
    private final DeviceConfig deviceConfig = new DeviceConfig();
    private final DeviceOp deviceOp = new DeviceOp();
    private final ListenerRegistration<GlobalConfig> configReg;
    private final ListenerRegistration<DeviceConfig> deviceReg;
    private final ListenerRegistration<DeviceOp> deviceOpReg;

    private final CallhomeStatusReporter statusReporter;

    CallHomeAuthProviderImpl(DataBroker broker) {
        configReg = broker.registerDataTreeChangeListener(GLOBAL, globalConfig);
        deviceReg = broker.registerDataTreeChangeListener(ALLOWED_DEVICES, deviceConfig);
        deviceOpReg = broker.registerDataTreeChangeListener(ALLOWED_OP_DEVICES, deviceOp);
        statusReporter = new CallhomeStatusReporter(broker);
    }

    @Nonnull
    @Override
    public CallHomeAuthorization provideAuth(SocketAddress remoteAddress, PublicKey serverKey) {
        Device deviceSpecific = deviceConfig.get(serverKey);
        String sessionName;
        Credentials deviceCred;

        if (deviceSpecific != null) {
            sessionName = deviceSpecific.getUniqueId();
            deviceCred = deviceSpecific.getCredentials();
        } else {
            String syntheticId = fromRemoteAddress(remoteAddress);
            if (globalConfig.allowedUnknownKeys()) {
                sessionName = syntheticId;
                deviceCred = null;
                statusReporter.asForceListedDevice(syntheticId, serverKey);
            } else {
                Device opDevice = deviceOp.get(serverKey);
                if (opDevice == null) {
                    statusReporter.asUnlistedDevice(syntheticId, serverKey);
                } else {
                    LOG.info("Repeating rejection of unlisted device with id of {}", opDevice.getUniqueId());
                }
                return CallHomeAuthorization.rejected();
            }
        }

        final Credentials credentials = deviceCred != null ? deviceCred : globalConfig.getCredentials();

        if (credentials == null) {
            LOG.info("No credentials found for {}, rejecting.", remoteAddress);
            return CallHomeAuthorization.rejected();
        }

        Builder authBuilder = CallHomeAuthorization.serverAccepted(sessionName, credentials.getUsername());
        for (String password : credentials.getPasswords()) {
            authBuilder.addPassword(password);
        }
        return authBuilder.build();
    }

    @Override
    public void close() throws Exception {
        configReg.close();
        deviceReg.close();
        deviceOpReg.close();
    }

    private String fromRemoteAddress(SocketAddress remoteAddress) {
        if (remoteAddress instanceof InetSocketAddress) {
            InetSocketAddress socketAddress = (InetSocketAddress) remoteAddress;
            return InetAddresses.toAddrString(socketAddress.getAddress()) + ":" + socketAddress.getPort();
        }
        return remoteAddress.toString();
    }

    private class DeviceConfig implements DataTreeChangeListener<Device> {

        private final AuthorizedKeysDecoder keyDecoder = new AuthorizedKeysDecoder();

        private ConcurrentMap<PublicKey, Device> byPublicKey = new ConcurrentHashMap<>();

        @Override
        public void onDataTreeChanged(Collection<DataTreeModification<Device>> mods) {
            for (DataTreeModification<Device> dataTreeModification : mods) {
                DataObjectModification<Device> rootNode = dataTreeModification.getRootNode();
                process(rootNode);
            }
        }

        private void process(DataObjectModification<Device> deviceMod) {
            Device before = deviceMod.getDataBefore();
            Device after = deviceMod.getDataAfter();

            if (before == null) {
                putDevice(after);
            } else if (after == null) {
                // Delete
                removeDevice(before);
            } else {
                if (!Objects.equal(before.getSshHostKey(), after.getSshHostKey())) {
                    // key changed // we should remove previous key.
                    removeDevice(before);
                }
                putDevice(after);
            }
        }

        private void putDevice(Device device) {
            PublicKey key = publicKey(device);
            if (key == null) {
                return;
            }
            byPublicKey.put(key, device);
        }

        private void removeDevice(Device device) {
            PublicKey key = publicKey(device);
            if (key == null) {
                return;
            }
            byPublicKey.remove(key);
        }

        private PublicKey publicKey(Device device) {
            String hostKey = device.getSshHostKey();
            try {
                return keyDecoder.decodePublicKey(hostKey);
            } catch (InvalidKeySpecException | NoSuchAlgorithmException | NoSuchProviderException e) {
                LOG.error("Unable to decode SSH key for {}. Ignoring update for this device", device.getUniqueId(), e);
                return null;
            }
        }

        private Device get(PublicKey key) {
            return byPublicKey.get(key);
        }
    }

    private class DeviceOp implements DataTreeChangeListener<Device> {

        private ConcurrentMap<String, Device> byPublicKey = new ConcurrentHashMap<>();

        @Override
        public void onDataTreeChanged(Collection<DataTreeModification<Device>> mods) {
            for (DataTreeModification<Device> dataTreeModification : mods) {
                DataObjectModification<Device> rootNode = dataTreeModification.getRootNode();
                process(rootNode);
            }
        }

        private void process(DataObjectModification<Device> deviceMod) {
            Device before = deviceMod.getDataBefore();
            Device after = deviceMod.getDataAfter();

            if (before == null) {
                putDevice(after);
            } else if (after == null) {
                // Delete
                removeDevice(before);
            } else {
                if (!Objects.equal(before.getSshHostKey(), after.getSshHostKey())) {
                    // key changed // we should remove previous key.
                    removeDevice(before);
                }
                putDevice(after);
            }
        }

        private void putDevice(Device device) {
            String key = device.getSshHostKey();
            byPublicKey.put(key, device);
        }

        private void removeDevice(Device device) {
            String key = device.getSshHostKey();
            byPublicKey.remove(key);
        }

        Device get(PublicKey serverKey) {
            String skey = "";

            try {
                skey = AuthorizedKeysDecoder.encodePublicKey(serverKey);
                return byPublicKey.get(skey);
            } catch (IOException | IllegalArgumentException e) {
                LOG.error("Unable to encode server key: {}", skey, e);
                return null;
            }
        }
    }

    private class GlobalConfig implements DataTreeChangeListener<Global> {

        private volatile Global current = null;

        @Override
        public void onDataTreeChanged(Collection<DataTreeModification<Global>> mods) {
            for (DataTreeModification<Global> dataTreeModification : mods) {
                current = dataTreeModification.getRootNode().getDataAfter();
            }
        }

        boolean allowedUnknownKeys() {
            if (current == null) {
                return false;
            }
            // Deal with null values.
            return Boolean.TRUE.equals(current.isAcceptAllSshKeys());
        }

        Credentials getCredentials() {
            return current != null ? current.getCredentials() : null;
        }
    }
}
