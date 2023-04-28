/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import com.google.common.collect.Iterables;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.callhome.protocol.AuthorizedKeysDecoder;
import org.opendaylight.netconf.callhome.protocol.CallHomeAuthorization;
import org.opendaylight.netconf.callhome.protocol.CallHomeAuthorization.Builder;
import org.opendaylight.netconf.callhome.protocol.CallHomeAuthorizationProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.NetconfCallhomeServer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.AllowedDevices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.Global;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.Global.MountPointNamingStrategy;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.Device;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.device.transport.Ssh;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.device.transport.ssh.SshClientParams;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CallHomeAuthProviderImpl implements CallHomeAuthorizationProvider, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(CallHomeAuthProviderImpl.class);

    private final @NonNull GlobalConfig globalConfig = new GlobalConfig();
    private final @NonNull DeviceConfig deviceConfig = new DeviceConfig();
    private final @NonNull DeviceOp deviceOp = new DeviceOp();
    private final ListenerRegistration<GlobalConfig> configReg;
    private final ListenerRegistration<DeviceConfig> deviceReg;
    private final ListenerRegistration<DeviceOp> deviceOpReg;

    private final CallhomeStatusReporter statusReporter;

    CallHomeAuthProviderImpl(final DataBroker broker) {
        configReg = broker.registerDataTreeChangeListener(
            DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(NetconfCallhomeServer.class).child(Global.class)),
            globalConfig);

        final var allowedDeviceWildcard =
            InstanceIdentifier.create(NetconfCallhomeServer.class).child(AllowedDevices.class).child(Device.class);

        deviceReg = broker.registerDataTreeChangeListener(
            DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION, allowedDeviceWildcard),
            deviceConfig);
        deviceOpReg = broker.registerDataTreeChangeListener(
            DataTreeIdentifier.create(LogicalDatastoreType.OPERATIONAL, allowedDeviceWildcard),
            deviceOp);
        statusReporter = new CallhomeStatusReporter(broker);
    }

    @Override
    public CallHomeAuthorization provideAuth(final SocketAddress remoteAddress, final PublicKey serverKey) {
        Device deviceSpecific = deviceConfig.get(serverKey);
        String sessionName;
        Credentials deviceCred;

        if (deviceSpecific != null) {
            sessionName = deviceSpecific.getUniqueId();
            if (deviceSpecific.getTransport() instanceof Ssh ssh) {
                final SshClientParams clientParams = ssh.getSshClientParams();
                deviceCred = clientParams.getCredentials();
            } else {
                deviceCred = deviceSpecific.getCredentials();
            }
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
    public void close() {
        configReg.close();
        deviceReg.close();
        deviceOpReg.close();
    }

    private String fromRemoteAddress(final SocketAddress remoteAddress) {
        if (remoteAddress instanceof InetSocketAddress socketAddress) {
            final var hostAddress = socketAddress.getAddress().getHostAddress();
            return switch (globalConfig.getMountPointNamingStrategy()) {
                case IPONLY -> hostAddress;
                case IPPORT -> hostAddress + ":" + socketAddress.getPort();
            };
        }
        return remoteAddress.toString();
    }

    private abstract static class AbstractDeviceListener implements DataTreeChangeListener<Device> {

        @Override
        public final void onDataTreeChanged(final Collection<DataTreeModification<Device>> mods) {
            for (DataTreeModification<Device> dataTreeModification : mods) {
                final DataObjectModification<Device> deviceMod = dataTreeModification.getRootNode();
                final ModificationType modType = deviceMod.getModificationType();
                switch (modType) {
                    case DELETE:
                        deleteDevice(deviceMod.getDataBefore());
                        break;
                    case SUBTREE_MODIFIED:
                    case WRITE:
                        deleteDevice(deviceMod.getDataBefore());
                        writeDevice(deviceMod.getDataAfter());
                        break;
                    default:
                        throw new IllegalStateException("Unhandled modification type " + modType);
                }
            }
        }

        private void deleteDevice(final Device dataBefore) {
            if (dataBefore != null) {
                final String publicKey = getHostPublicKey(dataBefore);
                if (publicKey != null) {
                    LOG.debug("Removing device {}", dataBefore.getUniqueId());
                    removeDevice(publicKey, dataBefore);
                } else {
                    LOG.debug("Ignoring removal of device {}, no host key present", dataBefore.getUniqueId());
                }
            }
        }

        private void writeDevice(final Device dataAfter) {
            final String publicKey = getHostPublicKey(dataAfter);
            if (publicKey != null) {
                LOG.debug("Adding device {}", dataAfter.getUniqueId());
                addDevice(publicKey, dataAfter);
            } else {
                LOG.debug("Ignoring addition of device {}, no host key present", dataAfter.getUniqueId());
            }
        }

        private static String getHostPublicKey(final Device device) {
            if (device.getTransport() instanceof Ssh ssh) {
                return ssh.getSshClientParams().getHostKey();
            } else {
                return device.getSshHostKey();
            }
        }

        abstract void addDevice(String publicKey, Device device);

        abstract void removeDevice(String publicKey, Device device);
    }

    private static class DeviceConfig extends AbstractDeviceListener {
        private final ConcurrentMap<PublicKey, Device> byPublicKey = new ConcurrentHashMap<>();
        private final AuthorizedKeysDecoder keyDecoder = new AuthorizedKeysDecoder();

        Device get(final PublicKey key) {
            return byPublicKey.get(key);
        }

        @Override
        void addDevice(final String publicKey, final Device device) {
            final PublicKey key = publicKey(publicKey, device);
            if (key != null) {
                byPublicKey.put(key, device);
            }
        }

        @Override
        void removeDevice(final String publicKey, final Device device) {
            final PublicKey key = publicKey(publicKey, device);
            if (key != null) {
                byPublicKey.remove(key);
            }
        }

        private PublicKey publicKey(final String hostKey, final Device device) {
            try {
                return keyDecoder.decodePublicKey(hostKey);
            } catch (GeneralSecurityException e) {
                LOG.error("Unable to decode SSH key for {}. Ignoring update for this device", device.getUniqueId(), e);
                return null;
            }
        }
    }

    private static class DeviceOp extends AbstractDeviceListener {
        private final ConcurrentMap<String, Device> byPublicKey = new ConcurrentHashMap<>();

        Device get(final PublicKey serverKey) {
            final String skey;
            try {
                skey = AuthorizedKeysDecoder.encodePublicKey(serverKey);
            } catch (IOException | IllegalArgumentException e) {
                LOG.error("Unable to encode server key: {}", serverKey, e);
                return null;
            }

            return byPublicKey.get(skey);
        }

        @Override
        void removeDevice(final String publicKey, final Device device) {
            byPublicKey.remove(publicKey);
        }

        @Override
        void addDevice(final String publicKey, final Device device) {
            byPublicKey.put(publicKey, device);
        }
    }

    private static class GlobalConfig implements DataTreeChangeListener<Global> {
        private volatile Global current = null;

        @Override
        public void onDataTreeChanged(final Collection<DataTreeModification<Global>> mods) {
            if (!mods.isEmpty()) {
                current = Iterables.getLast(mods).getRootNode().getDataAfter();
            }
        }

        boolean allowedUnknownKeys() {
            final Global local = current;
            // Deal with null values.
            return local != null && Boolean.TRUE.equals(local.getAcceptAllSshKeys());
        }

        Credentials getCredentials() {
            final Global local = current;
            return local != null ? local.getCredentials() : null;
        }

        @NonNull MountPointNamingStrategy getMountPointNamingStrategy() {
            final Global local = current;
            final MountPointNamingStrategy strat = local != null ? local.getMountPointNamingStrategy() : null;
            return strat == null ? MountPointNamingStrategy.IPPORT : strat;
        }
    }
}
