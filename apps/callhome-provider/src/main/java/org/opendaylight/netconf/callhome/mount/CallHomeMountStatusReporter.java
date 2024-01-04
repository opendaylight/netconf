/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.callhome.server.CallHomeStatusRecorder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.NetconfCallhomeServer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.AllowedDevices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.Device;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.Device.DeviceStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.DeviceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.DeviceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.device.transport.SshBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.device.transport.ssh.SshClientParamsBuilder;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service responsible for status update for call-home devices.
 */
@Component(service = {CallHomeMountStatusReporter.class, CallHomeStatusRecorder.class}, immediate = true)
@Singleton
public final class CallHomeMountStatusReporter implements CallHomeStatusRecorder, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(CallHomeMountStatusReporter.class);
    private static final InstanceIdentifier<AllowedDevices> ALL_DEVICES_II =
        InstanceIdentifier.create(NetconfCallhomeServer.class).child(AllowedDevices.class);

    private final DataBroker dataBroker;
    private final Registration syncReg;
    private final CallHomeMountService mountService;

    @Activate
    @Inject
    public CallHomeMountStatusReporter(
            final @Reference DataBroker broker,
            final @Reference CallHomeMountService mountService) {
        dataBroker = broker;
        this.mountService = mountService;
        syncReg = dataBroker.registerDataTreeChangeListener(
            DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION, ALL_DEVICES_II.child(Device.class)),
            this::onConfigurationDataTreeChanged);
    }

    @Deactivate
    @PreDestroy
    @Override
    public void close() {
        syncReg.close();
    }

    @Override
    public void reportSuccess(final String id) {
        updateCallHomeDeviceStatus(id, DeviceStatus.CONNECTED);
    }

    @Override
    public void reportDisconnected(final String id) {
        updateCallHomeDeviceStatus(id, DeviceStatus.DISCONNECTED);
    }

    @Override
    public void reportFailedAuth(final String id) {
        updateCallHomeDeviceStatus(id, DeviceStatus.FAILEDAUTHFAILURE);
    }

    @Override
    public void reportNetconfFailure(final String id) {
        updateCallHomeDeviceStatus(id, DeviceStatus.FAILED);
    }

    @Override
    public void reportUnknown(final SocketAddress address, final PublicKey publicKey) {
        // ignored --> the case is handled by ssh auth provider which are conditionally invoking
        // reportNewSshDevice() directly
    }

    /**
     * Update device status within operational datastore.
     *
     * @param id device id
     * @param status new status
     */
    public void updateCallHomeDeviceStatus(final String id, final DeviceStatus status) {
        LOG.debug("Setting status '{}' for call-home device {}.", status, id);
        final var instanceIdentifier = buildInstanceIdentifier(id);
        final var device = readDevice(instanceIdentifier);
        if (device == null) {
            LOG.warn("No call-home device '{}' found in operational datastore. Status update to '{}' is omitted.",
                id, status);
            return;
        }
        if (status == device.getDeviceStatus()) {
            LOG.debug("Call-home device '{}' already having status '{}'. Update omitted", id, status);
            return;
        }
        writeDevice(instanceIdentifier, new DeviceBuilder(device).setDeviceStatus(status).build());
    }

    /**
     * Persists new call-home device within operational datastore.
     *
     * @param id device id
     * @param serverKey public key used for device identification over ssh
     * @param status initial device status
     */
    public void reportNewSshDevice(final String id, final PublicKey serverKey, final DeviceStatus status) {
        writeDevice(buildInstanceIdentifier(id), newSshDevice(id, serverKey, status));
    }

    private static Device newSshDevice(final String id, final PublicKey serverKey, final DeviceStatus status) {
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
            .setTransport(new SshBuilder().setSshClientParams(
                new SshClientParamsBuilder().setHostKey(sshEncodedKey).build()).build())
            .setDeviceStatus(status)
            .build();
    }

    private @Nullable Device readDevice(final InstanceIdentifier<Device> instanceIdentifier) {
        try (var readTx = dataBroker.newReadOnlyTransaction()) {
            return readTx.read(LogicalDatastoreType.OPERATIONAL, instanceIdentifier).get().orElse(null);
        } catch (InterruptedException | ExecutionException e) {
            return null;
        }
    }

    private void writeDevice(final InstanceIdentifier<Device> instanceIdentifier, final Device device) {
        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.merge(LogicalDatastoreType.OPERATIONAL, instanceIdentifier, device);
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

    private static InstanceIdentifier<Device> buildInstanceIdentifier(final String id) {
        return ALL_DEVICES_II.child(Device.class, new DeviceKey(id));
    }

    // DataTreeChangeListener dedicated to call-home device data synchronization
    // from CONFIGURATION to OPERATIONAL datastore (excluding device status)
    private void onConfigurationDataTreeChanged(final Collection<DataTreeModification<Device>> changes) {
        final var deleted = ImmutableList.<InstanceIdentifier<Device>>builder();
        final var modified = ImmutableList.<Device>builder();
        for (var change : changes) {
            var changeRootNode = change.getRootNode();
            switch (changeRootNode.getModificationType()) {
                case SUBTREE_MODIFIED:
                case WRITE:
                    modified.add(changeRootNode.getDataAfter());
                    break;
                case DELETE:
                    deleted.add(change.getRootPath().getRootIdentifier());
                    break;
                default:
                    break;
            }
        }
        syncModifiedDevices(modified.build());
        syncDeletedDevices(deleted.build());
    }

    private void syncModifiedDevices(final List<Device> updatedDevices) {
        if (updatedDevices.isEmpty()) {
            return;
        }
        for (var configDevice : updatedDevices) {
            final var instanceIdentifier = buildInstanceIdentifier(configDevice.getUniqueId());
            final var operDevice = readDevice(instanceIdentifier);
            final var currentStatus = operDevice == null || operDevice.getDeviceStatus() == null
                ? DeviceStatus.DISCONNECTED : operDevice.getDeviceStatus();
            writeDevice(instanceIdentifier, new DeviceBuilder(configDevice).setDeviceStatus(currentStatus).build());
        }
    }

    public void syncDeletedDevices(final List<InstanceIdentifier<Device>> deletedDeviceIdentifiers) {
        if (deletedDeviceIdentifiers.isEmpty()) {
            return;
        }
        final var writeTx = dataBroker.newWriteOnlyTransaction();
        deletedDeviceIdentifiers.forEach(instancedIdentifier ->
            writeTx.delete(LogicalDatastoreType.OPERATIONAL, instancedIdentifier));

        writeTx.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("Device deletions committed");
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.warn("Failed to commit device deletions", cause);
            }
        }, MoreExecutors.directExecutor());

        // Disconnect deleted devices
        deletedDeviceIdentifiers.forEach(identifier -> {
            assert identifier instanceof KeyedInstanceIdentifier<?,?>;
            assert ((KeyedInstanceIdentifier<?,?>)identifier).getKey() instanceof DeviceKey;
            mountService.createSshSessionContextManager().remove(
                ((DeviceKey)((KeyedInstanceIdentifier<?,?>)identifier).getKey()).getUniqueId());
        });
    }
}
