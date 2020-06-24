/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount.tls;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.callhome.protocol.tls.TlsAllowedDevicesMonitor;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.trusted.certificates.TrustedCertificate;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.NetconfCallhomeServer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.netconf.callhome.server.AllowedDevices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.netconf.callhome.server.allowed.devices.Device;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TlsAllowedDevicesMonitorImpl implements TlsAllowedDevicesMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(TlsAllowedDevicesMonitorImpl.class);

    private static final InstanceIdentifier<Device> ALLOWED_DEVICES_PATH =
        InstanceIdentifier.create(NetconfCallhomeServer.class).child(AllowedDevices.class).child(Device.class);
    private static final DataTreeIdentifier<Device> ALLOWED_DEVICES =
        DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION, ALLOWED_DEVICES_PATH);
    private static final InstanceIdentifier<Keystore> KEYSTORE_PATH = InstanceIdentifier.create(Keystore.class);
    private static final DataTreeIdentifier<Keystore> KEYSTORE = DataTreeIdentifier.create(
        LogicalDatastoreType.CONFIGURATION, KEYSTORE_PATH);

    private static final ConcurrentMap<String, String> ALLOWED_KEYS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, String> ALLOWED_CERTIFICATES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, X509Certificate> TRUSTED_CERTIFICATES = new ConcurrentHashMap<>();

    public TlsAllowedDevicesMonitorImpl(DataBroker dataBroker) {
        dataBroker.registerDataTreeChangeListener(ALLOWED_DEVICES, new AllowedDevicesMonitor());
        dataBroker.registerDataTreeChangeListener(KEYSTORE, new CertificatesMonitor());
    }

    @Override
    public String findDeviceIdByCertificate(@NonNull Certificate certificate) {
        Set<String> certificateNames = TRUSTED_CERTIFICATES.entrySet().stream().filter(
            v -> certificate.equals(v.getValue())).map(Map.Entry::getKey).collect(Collectors.toSet());
        Set<String> deviceNames = ALLOWED_CERTIFICATES.entrySet().stream().filter(
            v -> certificateNames.contains(v.getValue())).map(Map.Entry::getKey).collect(Collectors.toSet());

        if (deviceNames.size() > 1) {
            LOG.error("Unable to find device by provided certificate. Possible reason: one certificate configured "
                + "with multiple devices/names");
        }
        return deviceNames.stream().findFirst().orElse(null);
    }

    private static class CertificatesMonitor implements ClusteredDataTreeChangeListener<Keystore> {

        @Override
        public void onDataTreeChanged(@NonNull Collection<DataTreeModification<Keystore>> changes) {
            changes.stream().map(DataTreeModification::getRootNode)
                .flatMap(v -> v.getModifiedChildren().stream())
                .filter(v -> v.getDataType().equals(TrustedCertificate.class))
                .map(v -> (DataObjectModification<TrustedCertificate>)v)
                .forEach(this::updateCertificate);
        }

        private void updateCertificate(DataObjectModification<TrustedCertificate> change) {
            final DataObjectModification.ModificationType modType = change.getModificationType();
            switch (modType) {
                case DELETE:
                    deleteCertificate(change.getDataBefore());
                    break;
                case SUBTREE_MODIFIED:
                case WRITE:
                    deleteCertificate(change.getDataBefore());
                    writeCertificate(change.getDataAfter());
                    break;
                default:
                    break;
            }
        }

        private void deleteCertificate(final TrustedCertificate dataBefore) {
            if (dataBefore != null) {
                LOG.debug("Removing trusted certificate {}", dataBefore.getName());
                TRUSTED_CERTIFICATES.remove(dataBefore.getName());
            }
        }

        private void writeCertificate(final TrustedCertificate dataAfter) {
            if (dataAfter != null) {
                LOG.debug("Adding trusted certificate {}", dataAfter.getName());
                TRUSTED_CERTIFICATES.putIfAbsent(dataAfter.getName(), buildCertificate(dataAfter.getCertificate()));
            }
        }

        private X509Certificate buildCertificate(String encoded) {
            byte[] decoded = Base64.getMimeDecoder().decode(encoded.getBytes(US_ASCII));
            try {
                CertificateFactory factory = CertificateFactory.getInstance("X.509");
                InputStream in = new ByteArrayInputStream(decoded);
                return (X509Certificate) factory.generateCertificate(in);
            } catch (CertificateException e) {
                LOG.error("Unable to build X.509 certificate from encoded value: {}", e.getLocalizedMessage());
            }
            return null;
        }

    }

    private static class AllowedDevicesMonitor implements ClusteredDataTreeChangeListener<Device> {

        @Override
        public final void onDataTreeChanged(final Collection<DataTreeModification<Device>> mods) {
            for (DataTreeModification<Device> dataTreeModification : mods) {
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
                        break;
                }
            }
        }

        private void deleteDevice(final Device dataBefore) {
            if (dataBefore != null) {
                LOG.debug("Removing device {}", dataBefore.getUniqueId());
                ALLOWED_KEYS.remove(dataBefore.getUniqueId());
                ALLOWED_CERTIFICATES.remove(dataBefore.getUniqueId());
            }
        }

        private void writeDevice(final Device dataAfter) {
            if (dataAfter != null) {
                LOG.debug("Adding device {}", dataAfter.getUniqueId());
                ALLOWED_KEYS.putIfAbsent(dataAfter.getUniqueId(), dataAfter.getTlsKeyId());
                ALLOWED_CERTIFICATES.putIfAbsent(dataAfter.getUniqueId(), dataAfter.getTlsCertId());
            }
        }
    }
}
