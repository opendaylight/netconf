/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 * RESTCONF server API.
 */
module org.opendaylight.restconf.server.api {
    exports org.opendaylight.restconf.server.api;

    requires transitive com.google.common;
    requires transitive org.opendaylight.netconf.databind;
    requires transitive org.opendaylight.netconf.wg.server.api;
    requires transitive org.opendaylight.restconf.api;
    requires transitive org.opendaylight.yangtools.yang.data.api;
    requires transitive org.opendaylight.yangtools.yang.data.codec.gson;
    requires transitive org.opendaylight.yangtools.yang.data.codec.xml;
    requires transitive org.opendaylight.yangtools.yang.data.util;
    requires transitive org.opendaylight.yangtools.yang.model.api;
    requires transitive org.opendaylight.yang.gen.ietf.subscribed.notifications.rfc8639;
    requires transitive org.opendaylight.yang.gen.ietf.yang.patch.rfc8072;
    requires org.opendaylight.yang.gen.ietf.restconf.rfc8040;
    requires org.opendaylight.yangtools.yang.data.impl;
    requires org.opendaylight.yangtools.yang.data.spi;
    requires org.slf4j;

    // Annotation-only dependencies
    requires static transitive org.eclipse.jdt.annotation;
    requires static org.osgi.annotation.bundle;
}
