/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.ssh.authentication;

import java.io.IOException;
import java.security.KeyPair;
import org.apache.sshd.ClientSession;
import org.apache.sshd.client.future.AuthFuture;
import org.opendaylight.aaa.encrypt.PKIUtil;

/**
 * Represents Auth information for the public key based authentication for netconf.
 */
public class PublicKeyAuth extends LoginPassword {
    private KeyPair keyPair = null;

    public PublicKeyAuth(String username, String password, String keyPath, String passPhrase) {
        super(username, password);
        try {
            this.keyPair = new PKIUtil().decodePrivateKey(keyPath, passPhrase);
        }catch(IOException ioEx){
            ioEx.printStackTrace();
        }
    }

    @Override
    public AuthFuture authenticate(final ClientSession session) throws IOException {
        session.addPublicKeyIdentity(keyPair);
        session.addPasswordIdentity(password);
        return session.auth();
    }
}
