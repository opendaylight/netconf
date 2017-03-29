/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.impl.sender;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Realm;
import com.ning.http.client.SSLEngineFactory;
import com.ning.http.client.providers.netty.NettyAsyncHttpProvider;
import com.ning.http.client.providers.netty.NettyAsyncHttpProviderConfig;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyHttpClientProvider implements HttpClientProvider {

    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpClientProvider.class);

    private static final NettyAsyncHttpProviderConfig NETTY_CONFIG = new NettyAsyncHttpProviderConfig();

    static {
        final NioClientSocketChannelFactory factory = new NioClientSocketChannelFactory();
        NETTY_CONFIG.setSocketChannelFactory(factory);
    }

    public NettyHttpClientProvider(final TrustStore trustStore) {
        NETTY_CONFIG.setSslEngineFactory(new RestconfSslEngineFactory(trustStore));
    }

    @Override
    public AsyncHttpClient createHttpClient(@Nullable final String principal, @Nullable final String password, final int idleTimeout,
                                            final int requestTimeout, final int connectTimeout, final boolean followRedirect) {
        final Realm realm = new Realm.RealmBuilder()
                .setScheme(Realm.AuthScheme.BASIC)
                .setPrincipal(principal)
                .setPassword(password)
                .setUsePreemptiveAuth(true)
                .build();
        final AsyncHttpClientConfig asyncHttpClientConfig = new AsyncHttpClientConfig.Builder()
                .setPooledConnectionIdleTimeout(idleTimeout)
                .setRequestTimeout(requestTimeout)
                .setConnectTimeout(connectTimeout)
                .setFollowRedirect(followRedirect)
                .setRealm(realm)
                .setAsyncHttpClientProviderConfig(NETTY_CONFIG)
                .build();
        return new AsyncHttpClient(NettyAsyncHttpProvider.class.getName(), asyncHttpClientConfig);
    }

    private static class RestconfSslEngineFactory implements SSLEngineFactory {

        private static final String SSLCONTEXT_FAILED = " Failed to initialize the server-side SSLContext";
        private final TrustStore trustStore;

        public RestconfSslEngineFactory(final TrustStore trustStore) {
            this.trustStore = trustStore;
        }

        @Override
        public SSLEngine newSSLEngine(final String peerHost, final int peerPort) throws GeneralSecurityException {
            SSLContext serverContext = null;
            try {
                final KeyStore ts = trustStore.createKeyStore();
                final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(ts);

                serverContext = SSLContext.getInstance("TLS");
                serverContext.init(null, tmf.getTrustManagers(), null);
            } catch (final IOException e) {
                LOG.warn("IOException - Failed to load keystore / truststore."
                        + SSLCONTEXT_FAILED, e);
            } catch (final NoSuchAlgorithmException e) {
                LOG.warn("NoSuchAlgorithmException - Unsupported algorithm."
                        + SSLCONTEXT_FAILED, e);
            } catch (final CertificateException e) {
                LOG.warn("CertificateException - Unable to access certificate (check password)."
                        + SSLCONTEXT_FAILED, e);
            } catch (final Exception e) {
                LOG.warn("Exception -" + SSLCONTEXT_FAILED, e);
            }
            final SSLEngine sslEngine = serverContext.createSSLEngine(peerHost, peerPort);
            sslEngine.setUseClientMode(true);
            return sslEngine;
        }
    }

}
