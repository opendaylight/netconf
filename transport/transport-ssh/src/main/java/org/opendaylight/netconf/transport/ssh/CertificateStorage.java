/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.asymmetric.key.with.certs.grouping.InlineOrKeystore;
//import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.end.entity.cert.with.key.grouping.InlineOrKeystore;

// FIXME: describe
abstract class CertificateStorage {

    final String resolveCerts(InlineOrKeystore keyWithCerts) {
        throw new UnsupportedOperationException();
    }

}
