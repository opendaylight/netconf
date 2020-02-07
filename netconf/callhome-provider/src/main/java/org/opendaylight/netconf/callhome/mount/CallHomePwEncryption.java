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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.binding.api.ReadTransaction;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.NetconfCallhomeServer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.credentials.CredentialsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.netconf.callhome.server.AllowedDevices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.netconf.callhome.server.Global;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.netconf.callhome.server.allowed.devices.Device;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.netconf.callhome.server.allowed.devices.DeviceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.netconf.callhome.server.allowed.devices.DeviceKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CallHomePwEncryption {

    private static final Logger LOG = LoggerFactory.getLogger(CallHomePwEncryption.class);
    private static final InstanceIdentifier<Credentials> GLOBAL_PATH =
            InstanceIdentifier.create(NetconfCallhomeServer.class).child(Global.class).child(Credentials.class);
    private static final DataTreeIdentifier<Credentials> GLOBAL_CREDENTIAL =
            DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION, GLOBAL_PATH);

    private static final InstanceIdentifier<Device> ALLOWED_DEVICES_PATH =
            InstanceIdentifier.create(NetconfCallhomeServer.class).child(AllowedDevices.class).child(Device.class);
    private static final DataTreeIdentifier<Device> ALLOWED_DEVICES =
            DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION, ALLOWED_DEVICES_PATH);

    private final GlobalConfig globalConfig = new GlobalConfig();
    private final DeviceConfig deviceConfig = new DeviceConfig();

    private final ListenerRegistration<GlobalConfig> configReg;
    private final ListenerRegistration<DeviceConfig> deviceReg;

    private final DataBroker dataBroker;
    private final AAAEncryptionService encryptionService;

    CallHomePwEncryption(final DataBroker dataBroker, final AAAEncryptionService encryptionService) {
        this.dataBroker = dataBroker;
        this.encryptionService = encryptionService;
        configReg = dataBroker.registerDataTreeChangeListener(GLOBAL_CREDENTIAL, globalConfig);
        deviceReg = dataBroker.registerDataTreeChangeListener(ALLOWED_DEVICES, deviceConfig);
    }

    public void close() {
        configReg.close();
        deviceReg.close();
    }

    private class GlobalConfig implements DataTreeChangeListener<Credentials> {

        @Override
        public void onDataTreeChanged(final Collection<DataTreeModification<Credentials>> mods) {
            for (DataTreeModification<Credentials> dataTreeModification : mods) {
                final DataObjectModification<Credentials> globalMod = dataTreeModification.getRootNode();
                final ModificationType modType = globalMod.getModificationType();
                switch (modType) {
                    case SUBTREE_MODIFIED:
                    case WRITE:
                        handleCredentialChange(globalMod);
                        break;
                    case DELETE:
                        LOG.debug("Global password config deleted");
                        break;
                    default:
                        throw new IllegalStateException("Unhandled modification type " + modType);
                }
            }
        }

        private void handleCredentialChange(DataObjectModification<Credentials> globalMod) {
            final List<String> newpasswords = globalMod.getDataAfter().getPasswords();
            if (!(newpasswords.isEmpty()) && newpasswords != null) {
                List<String> toBeEncrypted = new ArrayList<>();
                for (String s : newpasswords) {
                    if (!(isEncrypted(s))) {
                        toBeEncrypted.add(s);
                    }
                }
                if (!(toBeEncrypted.isEmpty())) {
                    encryptPassword(toBeEncrypted);
                } else {
                    LOG.debug("Password is already encrypted .");
                }
            } else {
                LOG.warn("Input password list is empty or null");
            }
        }

        private boolean isEncrypted(String input) {
            return !(input.equals(encryptionService.decrypt(input)));
        }

        private void encryptPassword(final List<String> passwords) {
            List<String> encryptedPasswords = new ArrayList<>();
            passwords.stream().forEach(s -> encryptedPasswords.add(encryptionService.encrypt(s)));
            LOG.info("Password encrypted successfully");
            setGlobalPassword(encryptedPasswords);
        }


        private void setGlobalPassword(final List<String> encryptedPasswords) {
            try (ReadTransaction configTx = dataBroker.newReadOnlyTransaction()) {
                Optional<Credentials> configGlobalCred = configTx.read(LogicalDatastoreType.CONFIGURATION, GLOBAL_PATH)
                        .get();
                Credentials credentials = configGlobalCred.orElse(null);
                if (credentials == null) {
                    LOG.warn("No corresponding global credential config found - exiting.");
                } else {
                    Credentials modifiedCred = withEncryptedPassword(credentials, encryptedPasswords);
                    if (modifiedCred == null) {
                        return;
                    }
                    LOG.debug("Setting encrypted password for global config.");
                    writeGlobal(modifiedCred);
                }
            } catch (InterruptedException | ExecutionException e) {
                return;
            }
        }

        private Credentials withEncryptedPassword(final Credentials configGlobal,
                final List<String> encryptedPasswords) {
            return new CredentialsBuilder()
                    .setUsername(configGlobal.getUsername())
                    .setPasswords(encryptedPasswords).build();
        }

        private void writeGlobal(final Credentials global) {
            WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
            tx.put(LogicalDatastoreType.CONFIGURATION, GLOBAL_PATH, global);
            commit(tx);
        }

        private void commit(final WriteTransaction tx) {
            tx.commit().addCallback(new FutureCallback<CommitInfo>() {
                @Override
                public void onSuccess(final CommitInfo result) {
                    LOG.debug("Global credentials config committed");
                }

                @Override
                public void onFailure(final Throwable cause) {
                    LOG.warn("Failed to commit global credentials config ", cause);
                }
            }, MoreExecutors.directExecutor());
        }
    }

    private class DeviceConfig implements DataTreeChangeListener<Device> {

        @Override
        public void onDataTreeChanged(final Collection<DataTreeModification<Device>> mods) {
            for (DataTreeModification<Device> dataTreeModification : mods) {
                final DataObjectModification<Device> deviceMod = dataTreeModification.getRootNode();
                final ModificationType modType = deviceMod.getModificationType();
                switch (modType) {
                    case SUBTREE_MODIFIED:
                    case WRITE:
                        handleCredentialChange(deviceMod);
                        break;
                    case DELETE:
                        LOG.debug("Device config deleted");
                        break;
                    default:
                        throw new IllegalStateException("Unhandled modification type " + modType);
                }
            }
        }

        private void handleCredentialChange(DataObjectModification<Device> deviceMod) {
            final List<String> newpasswords = deviceMod.getDataAfter().getCredentials().getPasswords();
            if (!(newpasswords.isEmpty()) && newpasswords != null) {
                List<String> toBeEncrypted = new ArrayList<>();
                for (String s : newpasswords) {
                    if (!(isEncrypted(s))) {
                        toBeEncrypted.add(s);
                    }
                }
                if (!(toBeEncrypted.isEmpty())) {
                    List<String> encryptedPasswords = encryptPassword(toBeEncrypted);
                    setDevicePassword(deviceMod.getDataAfter().key(), encryptedPasswords);
                } else {
                    LOG.debug("Password is already encrypted .");
                }
            } else {
                LOG.warn("Input password list is empty");
            }
        }

        private boolean isEncrypted(String input) {
            return !(input.equals(encryptionService.decrypt(input)));
        }

        private List<String> encryptPassword(final List<String> passwords) {
            List<String> encryptedPasswords = new ArrayList<>();
            if (passwords != null && !passwords.isEmpty()) {
                passwords.stream().forEach(s -> encryptedPasswords.add(encryptionService.encrypt(s)));
            }
            return encryptedPasswords;
        }

        private void setDevicePassword(final DeviceKey deviceKey, final List<String> encryptedPasswords) {
            try (ReadTransaction configTx = dataBroker.newReadOnlyTransaction()) {
                InstanceIdentifier<Device> deviceIID = buildDeviceInstanceIdentifier(deviceKey);
                Optional<Device> configDevice = configTx
                        .read(LogicalDatastoreType.CONFIGURATION, deviceIID).get();
                Device device = configDevice.orElse(null);
                if (device == null) {
                    LOG.warn("No corresponding device config found - exiting for {}", deviceKey.toString());
                } else {
                    Device modifiedDevice = withEncryptedPassword(device, encryptedPasswords);
                    if (modifiedDevice == null) {
                        return;
                    }
                    LOG.debug("Setting encrypted password for device config.");
                    writeDevice(deviceIID, modifiedDevice);
                }
            } catch (InterruptedException | ExecutionException e) {
                return;
            }
        }

        private InstanceIdentifier<Device> buildDeviceInstanceIdentifier(final DeviceKey deviceKey) {
            return InstanceIdentifier.create(NetconfCallhomeServer.class)
                    .child(AllowedDevices.class)
                    .child(Device.class, deviceKey);
        }

        private Device withEncryptedPassword(final Device configDevice,
                final List<String> encryptedPasswords) {
            Credentials credentials = new CredentialsBuilder()
                    .setUsername(configDevice.getCredentials().getUsername())
                    .setPasswords(encryptedPasswords).build();
            return new DeviceBuilder().setUniqueId(configDevice.getUniqueId())
                        .setSshHostKey(configDevice.getSshHostKey())
                        .setCredentials(credentials).build();
        }

        private void writeDevice(final InstanceIdentifier<Device> deviceIID, final Device device) {
            WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
            tx.put(LogicalDatastoreType.CONFIGURATION, deviceIID, device);
            commit(tx, device.key());
        }

        private void commit(final WriteTransaction tx, final DeviceKey device) {
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
    }
}
