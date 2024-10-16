/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 * Java API for the IETF keystore. It shadows {@code ietf-keystore.yang} by providing access to Java-native equivalents
 * to symmetirc and asymmetric keys.
 */
module org.opendaylight.netconf.keystore.api {
    exports org.opendaylight.netconf.keystore.api;

    requires transitive org.opendaylight.yang.gen.ietf.keystore.rfc9642;
    requires com.google.common;
    requires org.opendaylight.yangtools.binding.spec;

    // Annotation-only dependencies
    requires static transitive org.eclipse.jdt.annotation;
    requires static org.osgi.annotation.bundle;
}
