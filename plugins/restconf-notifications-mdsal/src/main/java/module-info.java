/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
module org.opendaylight.restconf.notofications.mdsal {
    exports org.opendaylight.restconf.notifications.mdsal;

    requires org.osgi.annotation.bundle;
    requires org.opendaylight.yangtools.yang.data.spi;
    requires org.slf4j;

    requires static transitive javax.inject;
    requires static org.osgi.service.component.annotations;
    requires org.opendaylight.mdsal.dom.api;
    requires org.opendaylight.restconf.server.spi;
    requires org.opendaylight.yang.gen.ietf.subscribed.notifications.rfc8639;
}