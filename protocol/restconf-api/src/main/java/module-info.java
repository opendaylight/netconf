/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
module org.opendaylight.restconf.api {
    exports org.opendaylight.restconf.api;
    exports org.opendaylight.restconf.api.query;

    requires transitive com.google.common;
    requires transitive org.opendaylight.yang.gen.ietf.yang.types.rfc6991;
    requires transitive org.opendaylight.yangtools.concepts;
    requires transitive org.opendaylight.yangtools.yang.common;
    requires org.opendaylight.odlparent.logging.markers;
    requires org.opendaylight.yangtools.yang.binding;
    requires org.slf4j;

    // Annotation-only dependencies
    requires static transitive org.eclipse.jdt.annotation;
}
