/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

import java.security.KeyPair;
import java.security.cert.X509Certificate;

record X509CertData(
        X509Certificate certificate,
        KeyPair keyPair,
        byte[] certBytes,
        byte[] publicKey,
        byte[] privateKey,
        byte[] sshPublicKey) {
    // Nothing else
}