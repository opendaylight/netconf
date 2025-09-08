/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 * Support for {@code ietf-crypto-types.yang}.
 */
module org.opendaylight.netconf.transport.crypto {
    exports org.opendaylight.netconf.transport.crypto;

    requires transitive org.opendaylight.netconf.transport.api;
    requires transitive org.opendaylight.yang.gen.ietf.crypto.types.rfc9640;

    requires com.google.common;
    requires org.opendaylight.yangtools.binding.spec;
    requires org.slf4j;

    // Annotation-only dependencies
    requires static transitive org.eclipse.jdt.annotation;
    requires static java.management;
    requires static org.osgi.annotation.bundle;
}
