/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.callhome;

import com.google.common.collect.ImmutableMultimap;
import io.netty.handler.ssl.SslContext;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectDeleted;
import org.opendaylight.mdsal.binding.api.DataObjectModification.WithDataAfter;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.client.SslContextFactory;
import org.opendaylight.netconf.client.mdsal.api.SslContextFactoryProvider;
import org.opendaylight.netconf.keystore.legacy.NetconfKeystore;
import org.opendaylight.netconf.keystore.legacy.NetconfKeystoreService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev240129.NetconfCallhomeServer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev240129.netconf.callhome.server.AllowedDevices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev240129.netconf.callhome.server.allowed.devices.Device;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev240129.netconf.callhome.server.allowed.devices.device.transport.Tls;
import org.opendaylight.yangtools.binding.DataObjectReference;
import org.opendaylight.yangtools.concepts.Registration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = CallHomeTlsAuthProvider.class, immediate = true)
@Singleton
public final class CallHomeMountTlsAuthProvider extends CallHomeTlsAuthProvider implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(CallHomeMountTlsAuthProvider.class);

    private final ConcurrentMap<String, String> deviceToPrivateKey = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> deviceToCertificate = new ConcurrentHashMap<>();
    private final Registration allowedDevicesReg;
    private final Registration certificatesReg;
    private final SslContextFactory sslContextFactory;

    private volatile ImmutableMultimap<PublicKey, String> publicKeyToName = ImmutableMultimap.of();

    @Inject
    @Activate
    public CallHomeMountTlsAuthProvider(@Reference final SslContextFactoryProvider sslContextFactoryProvider,
            @Reference final DataBroker dataBroker, @Reference final NetconfKeystoreService keystoreService) {
        sslContextFactory = sslContextFactoryProvider.getSslContextFactory(null);
        allowedDevicesReg = dataBroker.registerTreeChangeListener(LogicalDatastoreType.CONFIGURATION,
            DataObjectReference.builder(NetconfCallhomeServer.class).child(AllowedDevices.class).child(Device.class)
                .build(), new AllowedDevicesMonitor());
        certificatesReg = keystoreService.registerKeystoreConsumer(this::updateCertificates);
    }

    @PreDestroy
    @Deactivate
    @Override
    public void close() {
        allowedDevicesReg.close();
        certificatesReg.close();
    }

    private void updateCertificates(final NetconfKeystore keystore) {
        final var builder = ImmutableMultimap.<PublicKey, String>builder();
        keystore.trustedCertificates().forEach((name, cert) -> builder.put(cert.getPublicKey(), name));
        publicKeyToName = builder.build();
    }

    @Override
    public String idFor(final PublicKey key) {
        // Find certificate names by the public key
        final var certificateNames = publicKeyToName.get(key);

        // Find devices names associated with a certificate name
        final var deviceNames = deviceToCertificate.entrySet().stream()
            .filter(v -> certificateNames.contains(v.getValue()))
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
    protected SslContext getSslContext(final SocketAddress remoteAddress) {
        return sslContextFactory.createSslContext(Set.copyOf(deviceToPrivateKey.values()));
    }

    private final class AllowedDevicesMonitor implements DataTreeChangeListener<Device> {
        @Override
        public void onDataTreeChanged(final List<DataTreeModification<Device>> mods) {
            for (var dataTreeModification : mods) {
                switch (dataTreeModification.getRootNode()) {
                    case DataObjectDeleted<Device> deleted -> deleteDevice(deleted.dataBefore());
                    case WithDataAfter<Device> present -> {
                        deleteDevice(present.dataBefore());
                        writeDevice(present.dataAfter());
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
