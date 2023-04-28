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
import java.io.IOException;
import java.io.InputStream;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.NetconfCallhomeServer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.AllowedDevices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.Device;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev230428.netconf.callhome.server.allowed.devices.device.transport.Tls;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Component(service = TlsAllowedDevicesMonitor.class, immediate = true, property = "type=default")
public final class TlsAllowedDevicesMonitorImpl implements TlsAllowedDevicesMonitor, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(TlsAllowedDevicesMonitorImpl.class);

    private final ConcurrentMap<String, String> deviceToPrivateKey = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> deviceToCertificate = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PublicKey> certificateToPublicKey = new ConcurrentHashMap<>();
    private final Registration allowedDevicesReg;
    private final Registration certificatesReg;

    @Inject
    @Activate
    public TlsAllowedDevicesMonitorImpl(@Reference final DataBroker dataBroker) {
        allowedDevicesReg = dataBroker.registerDataTreeChangeListener(
            DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(NetconfCallhomeServer.class).child(AllowedDevices.class).child(Device.class)),
            new AllowedDevicesMonitor());
        certificatesReg = dataBroker.registerDataTreeChangeListener(
            DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Keystore.class)),
            new CertificatesMonitor());
    }

    @PreDestroy
    @Deactivate
    @Override
    public void close() {
        allowedDevicesReg.close();
        certificatesReg.close();
    }

    @Override
    public Optional<String> findDeviceIdByPublicKey(final PublicKey key) {
        // Find certificate names by the public key
        final Set<String> certificates = certificateToPublicKey.entrySet().stream()
            .filter(v -> key.equals(v.getValue()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

        // Find devices names associated with a certificate name
        final Set<String> deviceNames = deviceToCertificate.entrySet().stream()
            .filter(v -> certificates.contains(v.getValue()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

        // In real world scenario it is not possible to have multiple certificates with the same private/public key,
        // but in theor/synthetic tests it is practically possible to generate mulitple certificates from a single
        // private key. In such case it's not possible to pin certificate to particular device.
        if (deviceNames.size() > 1) {
            LOG.error("Unable to find device by provided certificate. Possible reason: one certificate configured "
                + "with multiple devices/names or multiple certificates contain same public key");
            return Optional.empty();
        } else {
            return deviceNames.stream().findFirst();
        }
    }

    @Override
    public Set<String> findAllowedKeys() {
        return Set.copyOf(deviceToPrivateKey.values());
    }

    private final class CertificatesMonitor implements ClusteredDataTreeChangeListener<Keystore> {
        @Override
        public void onDataTreeChanged(@NonNull final Collection<DataTreeModification<Keystore>> changes) {
            changes.stream().map(DataTreeModification::getRootNode)
                .flatMap(v -> v.getModifiedChildren().stream())
                .filter(v -> v.getDataType().equals(TrustedCertificate.class))
                .map(v -> (DataObjectModification<TrustedCertificate>) v)
                .forEach(this::updateCertificate);
        }

        private void updateCertificate(final DataObjectModification<TrustedCertificate> change) {
            switch (change.getModificationType()) {
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
                LOG.debug("Removing public key mapping for certificate {}", dataBefore.getName());
                certificateToPublicKey.remove(dataBefore.getName());
            }
        }

        private void writeCertificate(final TrustedCertificate dataAfter) {
            if (dataAfter != null) {
                LOG.debug("Adding public key mapping for certificate {}", dataAfter.getName());
                certificateToPublicKey.putIfAbsent(dataAfter.getName(), buildPublicKey(dataAfter.getCertificate()));
            }
        }

        private PublicKey buildPublicKey(final String encoded) {
            final byte[] decoded = Base64.getMimeDecoder().decode(encoded.getBytes(US_ASCII));
            try {
                final CertificateFactory factory = CertificateFactory.getInstance("X.509");
                try (InputStream in = new ByteArrayInputStream(decoded)) {
                    return factory.generateCertificate(in).getPublicKey();
                }
            } catch (final CertificateException | IOException e) {
                LOG.error("Unable to build X.509 certificate from encoded value: {}", e.getLocalizedMessage());
            }
            return null;
        }
    }

    private final class AllowedDevicesMonitor implements ClusteredDataTreeChangeListener<Device> {
        @Override
        public void onDataTreeChanged(final Collection<DataTreeModification<Device>> mods) {
            for (final DataTreeModification<Device> dataTreeModification : mods) {
                final DataObjectModification<Device> deviceMod = dataTreeModification.getRootNode();
                switch (deviceMod.getModificationType()) {
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
            if (dataBefore != null && dataBefore.getTransport() instanceof Tls) {
                LOG.debug("Removing device {}", dataBefore.getUniqueId());
                deviceToPrivateKey.remove(dataBefore.getUniqueId());
                deviceToCertificate.remove(dataBefore.getUniqueId());
            }
        }

        private void writeDevice(final Device dataAfter) {
            if (dataAfter != null && dataAfter.getTransport() instanceof Tls tls) {
                LOG.debug("Adding device {}", dataAfter.getUniqueId());
                final var tlsClientParams = tls.getTlsClientParams();
                deviceToPrivateKey.putIfAbsent(dataAfter.getUniqueId(), tlsClientParams.getKeyId());
                deviceToCertificate.putIfAbsent(dataAfter.getUniqueId(), tlsClientParams.getCertificateId());
            }
        }
    }
}
