/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import com.google.common.base.Strings;
import java.io.IOException;
import java.io.StringReader;
import java.security.KeyPair;
import java.util.Optional;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.aaa.encrypt.PKIUtil;
import org.opendaylight.netconf.client.mdsal.api.CredentialProvider;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.shaded.sshd.client.future.AuthFuture;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DatastoreBackedPublicKeyAuth extends AuthenticationHandler {
    private static final Logger LOG = LoggerFactory.getLogger(DatastoreBackedPublicKeyAuth.class);

    private final String username;
    private final String pairId;
    private final CredentialProvider credentialProvider;
    private final AAAEncryptionService encryptionService;

    // FIXME: do not use Optional here and deal with atomic set
    private Optional<KeyPair> keyPair = Optional.empty();

    public DatastoreBackedPublicKeyAuth(final String username, final String pairId,
                                        final CredentialProvider credentialProvider,
                                        final AAAEncryptionService encryptionService) {
        this.username = username;
        this.pairId = pairId;
        this.credentialProvider = credentialProvider;
        this.encryptionService = encryptionService;

        // try to immediately retrieve the pair from the adapter
        tryToSetKeyPair();
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public AuthFuture authenticate(final ClientSession session) throws IOException {
        // if we have keypair set the identity, otherwise retry the retrieval from the adapter
        // if successful set the identity.
        if (keyPair.isPresent() || tryToSetKeyPair()) {
            session.addPublicKeyIdentity(keyPair.orElseThrow());
        }
        return session.auth();
    }

    private boolean tryToSetKeyPair() {
        LOG.debug("Trying to retrieve keypair for: {}", pairId);
        final var dsKeypair = credentialProvider.credentialForId(pairId);
        if (dsKeypair == null) {
            LOG.debug("Unable to retrieve keypair for: {}", pairId);
            return false;
        }

        final String passPhrase = Strings.isNullOrEmpty(dsKeypair.getPassphrase()) ? "" : dsKeypair.getPassphrase();
        try {
            keyPair = Optional.of(new PKIUtil().decodePrivateKey(
                new StringReader(encryptionService.decrypt(dsKeypair.getPrivateKey()).replace("\\n", "\n")),
                encryptionService.decrypt(passPhrase)));
        } catch (IOException exception) {
            LOG.warn("Unable to decode private key, id={}", pairId, exception);
            return false;
        }
        return true;
    }
}
