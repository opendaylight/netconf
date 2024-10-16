/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static java.util.Objects.requireNonNull;

import java.security.KeyPair;
import java.util.Map;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.client.mdsal.api.CredentialProvider;
import org.opendaylight.netconf.keystore.legacy.NetconfKeystoreService;
import org.opendaylight.yangtools.concepts.Registration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

@Singleton
@Component(service = CredentialProvider.class)
@Deprecated(since = "8.0.3", forRemoval = true)
public final class DefaultCredentialProvider implements CredentialProvider, AutoCloseable {
    private final @NonNull Registration reg;

    private volatile @NonNull Map<String, KeyPair> credentials = Map.of();

    @Inject
    @Activate
    public DefaultCredentialProvider(@Reference final NetconfKeystoreService keystoreService) {
        reg = keystoreService.registerKeystoreConsumer(keystore -> {
            credentials = keystore.credentials();
        });
    }

    @Deactivate
    @PreDestroy
    @Override
    public void close() {
        reg.close();
    }

    @Override
    @Deprecated
    public KeyPair credentialForId(final String id) {
        return credentials.get(requireNonNull(id));
    }
}
