/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount.tls;

import static java.nio.charset.StandardCharsets.US_ASCII;

import io.netty.channel.Channel;
import io.netty.handler.ssl.SslHandler;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.callhome.server.tls.CallHomeTlsAuthProvider;
import org.opendaylight.netconf.client.SslHandlerFactory;
import org.opendaylight.netconf.client.mdsal.api.SslHandlerFactoryProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.trusted.certificates.TrustedCertificate;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev240129.NetconfCallhomeServer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev240129.netconf.callhome.server.AllowedDevices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev240129.netconf.callhome.server.allowed.devices.Device;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev240129.netconf.callhome.server.allowed.devices.device.transport.Tls;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Component(service = CallHomeTlsAuthProvider.class, immediate = true)
@Singleton
public final class CallHomeMountTlsAuthProvider implements CallHomeTlsAuthProvider, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(CallHomeMountTlsAuthProvider.class);

    private final ConcurrentMap<String, String> deviceToPrivateKey = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> deviceToCertificate = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PublicKey> certificateToPublicKey = new ConcurrentHashMap<>();
    private final Registration allowedDevicesReg;
    private final Registration certificatesReg;
    private final SslHandlerFactory sslHandlerFactory;

    @Inject
    @Activate
    public CallHomeMountTlsAuthProvider(
            final @Reference SslHandlerFactoryProvider sslHandlerFactoryProvider,
            final @Reference DataBroker dataBroker) {
        sslHandlerFactory = sslHandlerFactoryProvider.getSslHandlerFactory(null);
        allowedDevicesReg = dataBroker.registerTreeChangeListener(
            DataTreeIdentifier.of(LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(NetconfCallhomeServer.class).child(AllowedDevices.class).child(Device.class)),
            new AllowedDevicesMonitor());
        certificatesReg = dataBroker.registerTreeChangeListener(
            DataTreeIdentifier.of(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Keystore.class)),
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
    public String idFor(final PublicKey key) {
        // Find certificate names by the public key
        final var certificates = certificateToPublicKey.entrySet().stream()
            .filter(v -> key.equals(v.getValue()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

        // Find devices names associated with a certificate name
        final var deviceNames = deviceToCertificate.entrySet().stream()
            .filter(v -> certificates.contains(v.getValue()))
            .map(Map.Entry::getKey)
            .toList();

        // In real world scenario it is not possible to have multiple certificates with the same private/public key,
        // but in theory/synthetic tests it is practically possible to generate multiple certificates from a single
        // private key. In such case it's not possible to pin certificate to particular device.
        return switch (deviceNames.size()) {
            case 0 -> null;
            case 1 -> deviceNames.get(0);
            default -> {
                LOG.error("Unable to find device by provided certificate. Possible reason: one certificate configured "
                    + "with multiple devices/names or multiple certificates contain same public key");
                yield null;
            }
        };
    }

    @Override
    public SslHandler createSslHandler(final Channel channel) {
        return sslHandlerFactory.createSslHandler(Set.copyOf(deviceToPrivateKey.values()));
    }

    private final class CertificatesMonitor implements DataTreeChangeListener<Keystore> {
        @Override
        public void onDataTreeChanged(final List<DataTreeModification<Keystore>> changes) {
            changes.stream()
                .map(DataTreeModification::getRootNode)
                .flatMap(v -> v.modifiedChildren().stream())
                .filter(v -> v.dataType().equals(TrustedCertificate.class))
                .map(v -> (DataObjectModification<TrustedCertificate>) v)
                .forEach(this::updateCertificate);
        }

        private void updateCertificate(final DataObjectModification<TrustedCertificate> change) {
            switch (change.modificationType()) {
                case DELETE -> deleteCertificate(change.dataBefore());
                case SUBTREE_MODIFIED, WRITE -> {
                    deleteCertificate(change.dataBefore());
                    writeCertificate(change.dataAfter());
                }
                default -> {
                    // Should never happen
                }
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

    private final class AllowedDevicesMonitor implements DataTreeChangeListener<Device> {
        @Override
        public void onDataTreeChanged(final List<DataTreeModification<Device>> mods) {
            for (var dataTreeModification : mods) {
                final var deviceMod = dataTreeModification.getRootNode();
                switch (deviceMod.modificationType()) {
                    case DELETE -> deleteDevice(deviceMod.dataBefore());
                    case SUBTREE_MODIFIED, WRITE -> {
                        deleteDevice(deviceMod.dataBefore());
                        writeDevice(deviceMod.dataAfter());
                    }
                    default -> {
                        // Should never happen
                    }
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
