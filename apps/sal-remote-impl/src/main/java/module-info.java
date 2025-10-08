/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
module org.opendaylight.netconf.sal.remote.impl {
    exports org.opendaylight.netconf.sal.remote.impl;

    requires transitive org.opendaylight.restconf.mdsal.spi;
    requires transitive org.opendaylight.restconf.server.api;
    requires transitive org.opendaylight.restconf.server.spi;
    requires org.opendaylight.netconf.sal.remote;
    requires org.opendaylight.yang.gen.ietf.subscribed.notifications.rfc8639;
    requires org.opendaylight.yangtools.binding.spec;
    requires org.opendaylight.yangtools.yang.data.spi;
    requires org.slf4j;

    // Annotation-only dependencies
    requires static transitive javax.inject;
    requires static transitive org.eclipse.jdt.annotation;
    requires static org.osgi.service.component.annotations;
}
