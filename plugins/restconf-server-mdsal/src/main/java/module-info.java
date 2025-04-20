/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
module org.opendaylight.restconf.server.mdsal {
    exports org.opendaylight.restconf.server.mdsal;

    requires transitive org.opendaylight.restconf.mdsal.spi;
    requires transitive org.opendaylight.restconf.server.api;
    requires transitive org.opendaylight.restconf.server.spi;
    requires org.opendaylight.netconf.dom.api;
    requires org.opendaylight.restconf.subscription;
    requires org.opendaylight.yang.gen.ietf.restconf.monitoring.rfc8040;
    requires org.opendaylight.yang.gen.ietf.subscribed.notifications.rfc8639;
    requires org.opendaylight.yangtools.yang.data.spi;
    requires org.slf4j;

    // Annotation-only dependencies
    requires static transitive java.annotation;
    requires static transitive javax.inject;
    requires static transitive org.eclipse.jdt.annotation;
    requires static com.github.spotbugs.annotations;
    requires static org.checkerframework.checker.qual;
    requires static org.osgi.annotation.bundle;
    requires static org.osgi.service.component.annotations;
}
