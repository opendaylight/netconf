/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.truststore.api;

import java.security.cert.X509Certificate;
import java.util.Map;

/**
 * Primary interface to the truststore.
 */
public interface TruststoreContent {
    /**
     * The set of certificates indexed by their arbitrary name.
     *
     * @return The set of certificates
     */
    Map<String, X509Certificate> certificates();
}
