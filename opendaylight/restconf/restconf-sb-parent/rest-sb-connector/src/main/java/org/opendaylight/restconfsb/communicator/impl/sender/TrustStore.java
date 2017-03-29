/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.impl.sender;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public class TrustStore {

    private final String pathToTrustStore;
    private final String trustStorePassword;
    private final String trustStoreType;
    private final String truststorePathType;
    private KeyStore store;

    public TrustStore(final String pathToTrustStore, final String trustStorePassword, final String trustStoreType, final String truststorePathType) {
        this.pathToTrustStore = pathToTrustStore;
        this.trustStorePassword = trustStorePassword;
        this.trustStoreType = trustStoreType;
        this.truststorePathType = truststorePathType;
    }

    KeyStore createKeyStore() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        store = KeyStore.getInstance(trustStoreType);
        if ("CLASSPATH".equals(truststorePathType)) {
            store.load(getClass().getResourceAsStream(pathToTrustStore),
                    trustStorePassword.toCharArray());
        } else if ("PATH".equals(truststorePathType)) {
            store.load(new FileInputStream(pathToTrustStore),
                    trustStorePassword.toCharArray());
        } else {
            throw new IllegalStateException("Unknown path type");
        }
        return store;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final TrustStore that = (TrustStore) o;

        if (pathToTrustStore != null ? !pathToTrustStore.equals(that.pathToTrustStore) : that.pathToTrustStore != null) {
            return false;
        }
        if (trustStorePassword != null ? !trustStorePassword.equals(that.trustStorePassword) : that.trustStorePassword != null) {
            return false;
        }
        if (trustStoreType != null ? !trustStoreType.equals(that.trustStoreType) : that.trustStoreType != null) {
            return false;
        }
        return truststorePathType != null ? truststorePathType.equals(that.truststorePathType) : that.truststorePathType == null;

    }

    @Override
    public int hashCode() {
        int result = pathToTrustStore != null ? pathToTrustStore.hashCode() : 0;
        result = 31 * result + (trustStorePassword != null ? trustStorePassword.hashCode() : 0);
        result = 31 * result + (trustStoreType != null ? trustStoreType.hashCode() : 0);
        result = 31 * result + (truststorePathType != null ? truststorePathType.hashCode() : 0);
        return result;
    }


}
