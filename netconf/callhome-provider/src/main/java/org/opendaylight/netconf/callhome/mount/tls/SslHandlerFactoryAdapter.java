/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount.tls;

import io.netty.handler.ssl.SslHandler;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.client.SslHandlerFactory;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfKeystoreAdapter;
import org.opendaylight.netconf.sal.connect.util.SslHandlerFactoryImpl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.NetconfCallhomeServer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.netconf.callhome.server.AllowedDevices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.netconf.callhome.server.allowed.devices.Device;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.netconf.callhome.server.allowed.devices.device.transport.Tls;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SslHandlerFactoryAdapter implements SslHandlerFactory {

    private static final InstanceIdentifier<Device> ALLOWED_DEVICES_PATH =
        InstanceIdentifier.create(NetconfCallhomeServer.class).child(AllowedDevices.class).child(Device.class);
    private static final DataTreeIdentifier<Device> ALLOWED_DEVICES =
        DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION, ALLOWED_DEVICES_PATH);

    private static final Logger LOG = LoggerFactory.getLogger(SslHandlerFactoryAdapter.class);
    private static final ConcurrentMap<String, String> ALLOWED_KEYS = new ConcurrentHashMap<>();

    private final SslHandlerFactory sslHandlerFactory;
    private final NetconfKeystoreAdapter keystoreAdapter;
    private final DeviceListener deviceListener = new DeviceListener();
    private final ListenerRegistration<DeviceListener> deviceListenerListenerReg;

    public SslHandlerFactoryAdapter(final DataBroker dataBroker) {
        this.keystoreAdapter = new NetconfKeystoreAdapter(dataBroker);
        this.sslHandlerFactory = new SslHandlerFactoryImpl(keystoreAdapter);
        this.deviceListenerListenerReg = dataBroker.registerDataTreeChangeListener(ALLOWED_DEVICES, deviceListener);
    }

    @Override
    public SslHandler createSslHandler() {
        return createSslHandlerFilteredByKeys();
    }

    @Override
    public SslHandler createSslHandler(final Set<String> allowedKeys) {
        return createSslHandlerFilteredByKeys();
    }

    private SslHandler createSslHandlerFilteredByKeys() {
        if (ALLOWED_KEYS.isEmpty()) {
            LOG.error("No associated keys for TLS authentication were found");
            throw new IllegalStateException("No associated keys for TLS authentication were found");
        } else {
            return sslHandlerFactory.createSslHandler(new HashSet<>(ALLOWED_KEYS.values()));
        }
    }

    private static class DeviceListener implements DataTreeChangeListener<Device> {

        @Override
        public final void onDataTreeChanged(final Collection<DataTreeModification<Device>> mods) {
            for (final DataTreeModification<Device> dataTreeModification : mods) {
                final DataObjectModification<Device> deviceMod = dataTreeModification.getRootNode();
                final DataObjectModification.ModificationType modType = deviceMod.getModificationType();
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
            if (dataBefore != null && dataBefore.getTransport() instanceof Tls) {
                LOG.debug("Removing device {}", dataBefore.getUniqueId());
                ALLOWED_KEYS.remove(dataBefore.getUniqueId());
            }
        }

        private void writeDevice(final Device dataAfter) {
            if (dataAfter != null && dataAfter.getTransport() instanceof Tls) {
                LOG.debug("Adding device {}", dataAfter.getUniqueId());
                final String tlsKeyId = ((Tls) dataAfter.getTransport()).getTlsClientParams().getKeyId();
                ALLOWED_KEYS.putIfAbsent(dataAfter.getUniqueId(), tlsKeyId);
            }
        }
    }
}