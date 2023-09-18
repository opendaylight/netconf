/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
module org.opendaylight.restconf.server {
    exports org.opendaylight.restconf.server.api;
    exports org.opendaylight.restconf.server.spi;

    requires transitive com.google.common;
    requires transitive org.opendaylight.restconf.api;
    requires transitive org.opendaylight.yangtools.yang.common;
    requires transitive org.opendaylight.yangtools.yang.data.api;
    requires failureaccess;

    // Annotations
    requires static transitive org.eclipse.jdt.annotation;
    requires org.opendaylight.yangtools.yang.common;
}
