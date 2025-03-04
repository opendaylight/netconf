/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 * RESTCONF server SPI.
 */
module org.opendaylight.restconf.server.spi {
    exports org.opendaylight.restconf.server.spi;

    requires transitive com.google.common;
    requires transitive org.opendaylight.netconf.databind;
    requires transitive org.opendaylight.restconf.api;
    requires transitive org.opendaylight.restconf.server.api;
    requires org.opendaylight.netconf.api;
    requires org.opendaylight.yangtools.yang.data.codec.xml;
    requires org.opendaylight.yangtools.yang.data.spi;
    requires org.opendaylight.yangtools.yang.model.export;
    requires org.slf4j;

    // Annotation-only dependencies
    requires static transitive org.eclipse.jdt.annotation;
    requires static com.github.spotbugs.annotations;
    requires static org.osgi.annotation.bundle;
    requires org.opendaylight.yang.gen.ietf.subscribed.notifications.rfc8639;
}
