/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.X509TrustManager;

/**
 * An {@link X509TrustManager} delegating requests to some delegates.
 */
final class CompositeX509TrustManager implements X509TrustManager {
    private final X509TrustManager[] delegates;

    private X509Certificate[] acceptedIssuers;

    CompositeX509TrustManager(final List<X509TrustManager> delegates) {
        this.delegates = delegates.toArray(new X509TrustManager[0]);
    }

    @Override
    public void checkClientTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
        final var errors = new ArrayList<CertificateException>();
        for (var delegate : delegates) {
            try {
                delegate.checkClientTrusted(chain, authType);
                return;
            } catch (CertificateException e) {
                errors.add(e);
            }
        }
        throw newException(errors);
    }

    @Override
    public void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
        final var errors = new ArrayList<CertificateException>();
        for (var delegate : delegates) {
            try {
                delegate.checkServerTrusted(chain, authType);
                return;
            } catch (CertificateException e) {
                errors.add(e);
            }
        }
        throw newException(errors);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        if (acceptedIssuers == null) {
            final var list = new ArrayList<X509Certificate>();
            for (var delegate : delegates) {
                list.addAll(Arrays.asList(delegate.getAcceptedIssuers()));
            }
            acceptedIssuers = list.toArray(new X509Certificate[0]);
        }
        return acceptedIssuers.clone();
    }

    private static CertificateException newException(final List<CertificateException> errors) {
        final var ret = new CertificateException("No delegate trusts the certificate chain");
        errors.forEach(ret::addSuppressed);
        return ret;
    }
}
