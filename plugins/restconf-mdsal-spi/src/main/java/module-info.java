/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
module org.opendaylight.restconf.mdsal.spi {
    exports org.opendaylight.restconf.mdsal.spi;
    // FIXME: do not export this
    exports org.opendaylight.restconf.mdsal.spi.data;

    requires transitive org.opendaylight.mdsal.common.api;
    requires transitive org.opendaylight.mdsal.dom.api;
    requires transitive org.opendaylight.restconf.server.api;
    requires transitive org.opendaylight.restconf.server.spi;
    requires com.google.gson;
    requires org.opendaylight.netconf.api;
    requires org.opendaylight.netconf.dom.api;
    requires org.opendaylight.odlparent.logging.markers;
    requires org.opendaylight.yang.gen.ietf.restconf.rfc8040;
    requires org.opendaylight.yangtools.binding.spec;
    requires org.opendaylight.yangtools.yang.data.codec.xml;
    requires org.opendaylight.yangtools.yang.data.impl;
    requires org.opendaylight.yangtools.yang.data.spi;
    requires org.slf4j;

    // Annotation-only dependencies
    requires static transitive org.eclipse.jdt.annotation;
    requires static com.github.spotbugs.annotations;
    requires static org.osgi.annotation.bundle;
}
