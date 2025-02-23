/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
module org.opendaylight.netconf.databind {
    exports org.opendaylight.netconf.databind;

    requires transitive org.opendaylight.yangtools.yang.common;
    requires transitive org.opendaylight.yangtools.yang.data.codec.gson;
    requires transitive org.opendaylight.yangtools.yang.data.codec.xml;
    requires transitive org.opendaylight.yangtools.yang.data.util;
    requires transitive org.opendaylight.yangtools.yang.model.api;
    requires com.google.common;
    requires org.slf4j;

    // Annotation-only dependencies
    requires static transitive org.eclipse.jdt.annotation;
    requires static com.github.spotbugs.annotations;
    requires static org.osgi.annotation.bundle;
}
