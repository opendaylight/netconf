/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount.tls;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableSet;
import io.netty.handler.ssl.SslHandler;
import java.util.Collection;
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
import org.opendaylight.yangtools.concepts.AbstractRegistration;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SslHandlerFactoryAdapter extends AbstractRegistration implements SslHandlerFactory {
    private static final DataTreeIdentifier<Device> ALLOWED_DEVICES =
        DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION,
            InstanceIdentifier.builder(NetconfCallhomeServer.class).child(AllowedDevices.class).child(Device.class)
                .build());

    private static final Logger LOG = LoggerFactory.getLogger(SslHandlerFactoryAdapter.class);

    private final DeviceListener deviceListener = new DeviceListener();
    private final NetconfKeystoreAdapter keystoreAdapter;
    private final SslHandlerFactory sslHandlerFactory;
    private final Registration deviceListenerReg;

    public SslHandlerFactoryAdapter(final DataBroker dataBroker) {
        this.keystoreAdapter = new NetconfKeystoreAdapter(dataBroker);
        this.sslHandlerFactory = new SslHandlerFactoryImpl(keystoreAdapter);
        this.deviceListenerReg = dataBroker.registerDataTreeChangeListener(ALLOWED_DEVICES, deviceListener);
    }

    @Override
    public SslHandler createSslHandler() {
        return createSslHandlerFilteredByKeys();
    }

    @Override
    public SslHandler createSslHandler(final Set<String> allowedKeys) {
        return createSslHandlerFilteredByKeys();
    }

    @Override
    protected void removeRegistration() {
        deviceListenerReg.close();
    }

    private SslHandler createSslHandlerFilteredByKeys() {
        return sslHandlerFactory.createSslHandler(deviceListener.getAllowedKeys());
    }

    private static final class DeviceListener implements DataTreeChangeListener<Device> {
        private final ConcurrentMap<String, String> allowedKeys = new ConcurrentHashMap<>();

        @Override
        public void onDataTreeChanged(final Collection<DataTreeModification<Device>> mods) {
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

        Set<String> getAllowedKeys() {
            final Set<String> ret = ImmutableSet.copyOf(allowedKeys.values());
            checkState(!ret.isEmpty(), "No associated keys for TLS authentication were found");
            return ret;
        }

        private void deleteDevice(final Device dataBefore) {
            if (dataBefore != null && dataBefore.getTransport() instanceof Tls) {
                LOG.debug("Removing device {}", dataBefore.getUniqueId());
                allowedKeys.remove(dataBefore.getUniqueId());
            }
        }

        private void writeDevice(final Device dataAfter) {
            if (dataAfter != null && dataAfter.getTransport() instanceof Tls) {
                LOG.debug("Adding device {}", dataAfter.getUniqueId());
                final String tlsKeyId = ((Tls) dataAfter.getTransport()).getTlsClientParams().getKeyId();
                allowedKeys.putIfAbsent(dataAfter.getUniqueId(), tlsKeyId);
            }
        }
    }
}