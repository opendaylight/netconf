/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.ssh.authentication;

import com.google.common.base.Preconditions;

import java.io.IOException;
import java.security.KeyPair;

import org.apache.sshd.ClientSession;
import org.apache.sshd.client.future.AuthFuture;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.aaa.encrypt.PKIUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents Auth information for the public key based authentication for netconf.
 */
public
class PublicKeyAuth extends LoginPassword {
    private KeyPair keyPair = null;
    private static final Logger  LOG     = LoggerFactory.getLogger(PublicKeyAuth.class);

    public
    PublicKeyAuth(String username, String password, final AAAEncryptionService encryptionService,
                  String keyPath, String passPhrase) {
        super(username, password, encryptionService);
        try {
            Preconditions.checkState(keyPath != null && !keyPath.equals(""),
                                     "Private key path not specified");
            Preconditions.checkNotNull(passPhrase != null,
                                       "Private key passphrase not specified");
            this.keyPair = new PKIUtil().decodePrivateKey(keyPath, passPhrase);
        } catch (IOException ioEx) {
            LOG.warn("Not able to read the private key and passphrase for netconf client");
        }
    }

    @Override
    public
    AuthFuture authenticate(final ClientSession session) throws IOException {
        if (keyPair != null) session.addPublicKeyIdentity(keyPair);
        session.addPasswordIdentity(password);
        return session.auth();
    }
}
