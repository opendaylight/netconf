/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
module org.opendaylight.restconf.notifications.mdsal {
    exports org.opendaylight.restconf.notifications.mdsal;

    requires transitive org.opendaylight.mdsal.dom.api;
    requires transitive org.opendaylight.yangtools.yang.common;
    requires org.opendaylight.netconf.common.mdsal;
    requires org.opendaylight.yang.gen.ietf.yang.types.rfc6991;
    requires org.opendaylight.yang.gen.ietf.restconf.subscribed.notifications.rfc8650;
    requires org.opendaylight.yang.gen.ietf.subscribed.notifications.rfc8639;
    requires org.opendaylight.yangtools.yang.data.spi;
    requires org.slf4j;

    // Annotations
    requires static transitive javax.inject;
    requires static org.osgi.service.component.annotations;
    requires static org.osgi.annotation.bundle;
}
