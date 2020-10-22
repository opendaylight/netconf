/*
 * Copyright (c) 2019 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nativ.netconf.communicator.protocols.ssh;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.Sets;
import io.netty.handler.ssl.SslHandler;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Collections;
import java.util.Set;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.client.SslHandlerFactory;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.parameters.protocol.Specification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.parameters.protocol.specification.TlsCase;

public final class SslHandlerFactoryImpl implements SslHandlerFactory {
    private final NativeNetconfKeystore keystore;
    private final @Nullable Specification specification;

    public SslHandlerFactoryImpl(final NativeNetconfKeystore keystore) {
        this(keystore, null);
    }

    public SslHandlerFactoryImpl(final NativeNetconfKeystore keystore, final Specification specification) {
        this.keystore = requireNonNull(keystore);
        this.specification = specification;
    }

    @Override
    public SslHandler createSslHandler() {
        return createSslHandler(Collections.emptySet());
    }

    @Override
    public SslHandler createSslHandler(Set<String> allowedKeys) {
        try {
            final KeyStore keyStore = keystore.getJavaKeyStore(allowedKeys);

            final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, "".toCharArray());

            final TrustManagerFactory tmf =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);

            final SSLContext sslCtx = SSLContext.getInstance("TLS");
            sslCtx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            final SSLEngine engine = sslCtx.createSSLEngine();
            engine.setUseClientMode(true);

            final String[] engineProtocols = engine.getSupportedProtocols();
            final String[] enabledProtocols;
            if (specification != null) {
                checkArgument(specification instanceof TlsCase, "Cannot get TLS specification from: %s", specification);

                final Set<String> protocols = Sets.newHashSet(engineProtocols);
                protocols.removeAll(((TlsCase)specification).getTls().getExcludedVersions());
                enabledProtocols = protocols.toArray(new String[0]);
            } else {
                enabledProtocols = engineProtocols;
            }

            engine.setEnabledProtocols(enabledProtocols);
            engine.setEnabledCipherSuites(engine.getSupportedCipherSuites());
            engine.setEnableSessionCreation(true);
            return new SslHandler(engine);
        } catch (GeneralSecurityException | IOException exc) {
            throw new IllegalStateException(exc);
        }
    }
}