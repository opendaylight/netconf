/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 * Support for {@code ietf-keystore.yang}.
 */
module org.opendaylight.netconf.transport.keystore {
    exports org.opendaylight.netconf.transport.keystore;

    requires transitive org.opendaylight.netconf.keystore.api;
    requires transitive org.opendaylight.netconf.transport.api;
    requires transitive org.opendaylight.yang.gen.ietf.keystore.rfc9642;
    requires org.opendaylight.netconf.transport.crypto;

    // Annotation-only dependencies
    requires static transitive com.google.errorprone.annotations;
    requires static transitive org.eclipse.jdt.annotation;
    requires static org.osgi.annotation.bundle;
}
