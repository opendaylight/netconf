/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.legacy;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.singleton.api.ClusterSingletonService;
import org.opendaylight.mdsal.singleton.api.ServiceGroupIdentifier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.AddKeystoreEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.AddPrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.AddTrustedCertificate;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.RemoveKeystoreEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.RemovePrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.RemoveTrustedCertificate;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.Rpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RpcSingleton implements ClusterSingletonService {
    private static final Logger LOG = LoggerFactory.getLogger(RpcSingleton.class);
    private static final @NonNull ServiceGroupIdentifier SGI = new ServiceGroupIdentifier("netconf-keystore-rpc");

    private final AAAEncryptionService encryptionService;
    private final RpcProviderService rpcProvider;
    private final DataBroker dataBroker;

    private Registration reg;

    RpcSingleton(final DataBroker dataBroker, final RpcProviderService rpcProvider,
            final AAAEncryptionService encryptionService) {
        this.dataBroker = requireNonNull(dataBroker);
        this.rpcProvider = requireNonNull(rpcProvider);
        this.encryptionService = requireNonNull(encryptionService);
    }

    @Override
    public ServiceGroupIdentifier getIdentifier() {
        return SGI;
    }

    @Override
    public void instantiateServiceInstance() {
        if (reg != null) {
            LOG.warn("Instiatiating while already running, very weird", new Throwable());
            return;
        }

        LOG.debug("Registering RPC implementations");
        reg = rpcProvider.registerRpcImplementations(ImmutableClassToInstanceMap.<Rpc<?, ?>>builder()
            .put(AddKeystoreEntry.class, new DefaultAddKeystoreEntry(dataBroker, encryptionService))
            .put(RemoveKeystoreEntry.class, new DefaultRemoveKeystoreEntry(dataBroker))
            .put(AddPrivateKey.class, new DefaultAddPrivateKey(dataBroker))
            .put(RemovePrivateKey.class, new DefaultRemovePrivateKey(dataBroker))
            .put(AddTrustedCertificate.class, new DefaultAddTrustedCertificate(dataBroker))
            .put(RemoveTrustedCertificate.class, new DefaultRemoveTrustedCertificate(dataBroker))
            .build());
        LOG.info("This node is now owning NETCONF keystore configuration");
    }

    @Override
    public ListenableFuture<?> closeServiceInstance() {
        if (reg != null) {
            LOG.debug("Unregistering RPC implementations");
            reg.close();
            reg = null;
            LOG.info("This node is no longer owning NETCONF keystore configuration");
        }
        return Futures.immediateVoidFuture();
    }
}
