/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.northbound.ssh;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import io.netty.channel.local.LocalAddress;
import java.net.InetSocketAddress;
import java.util.Optional;
import org.opendaylight.netconf.auth.AuthProvider;
import org.opendaylight.netconf.shaded.sshd.common.keyprovider.KeyPairProvider;
import org.opendaylight.netconf.shaded.sshd.server.auth.pubkey.PublickeyAuthenticator;

public final class SshProxyServerConfiguration {
    private final InetSocketAddress bindingAddress;
    private final LocalAddress localAddress;
    private final AuthProvider authenticator;
    private final KeyPairProvider keyPairProvider;
    private final int idleTimeout;
    private final Optional<PublickeyAuthenticator> publickeyAuthenticator;

    SshProxyServerConfiguration(final InetSocketAddress bindingAddress, final LocalAddress localAddress,
                                final AuthProvider authenticator, final PublickeyAuthenticator publickeyAuthenticator,
                                final KeyPairProvider keyPairProvider, final int idleTimeout) {
        this.bindingAddress = requireNonNull(bindingAddress);
        this.localAddress = requireNonNull(localAddress);
        this.authenticator = requireNonNull(authenticator);
        this.keyPairProvider = requireNonNull(keyPairProvider);
        // Idle timeout cannot be disabled in the sshd by using =< 0 value
        checkArgument(idleTimeout > 0, "Idle timeout has to be > 0");
        this.idleTimeout = idleTimeout;
        this.publickeyAuthenticator = Optional.ofNullable(publickeyAuthenticator);
    }

    public InetSocketAddress getBindingAddress() {
        return bindingAddress;
    }

    public LocalAddress getLocalAddress() {
        return localAddress;
    }

    public AuthProvider getAuthenticator() {
        return authenticator;
    }

    public KeyPairProvider getKeyPairProvider() {
        return keyPairProvider;
    }

    public int getIdleTimeout() {
        return idleTimeout;
    }

    public Optional<PublickeyAuthenticator> getPublickeyAuthenticator() {
        return publickeyAuthenticator;
    }
}
