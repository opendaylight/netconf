/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
import org.opendaylight.restconf.subscription.impl.IetfSubscriptionFeatureProvider;
import org.opendaylight.yangtools.binding.meta.YangFeatureProvider;

module org.opendaylight.restconf.subscription {
    exports org.opendaylight.restconf.subscription;

    provides YangFeatureProvider with IetfSubscriptionFeatureProvider;

    requires transitive org.opendaylight.mdsal.dom.api;
    requires transitive org.opendaylight.restconf.server.spi;
    requires transitive org.opendaylight.yangtools.concepts;
    requires transitive org.opendaylight.yangtools.yang.common;
    requires org.opendaylight.yangtools.binding.spec;
    requires org.opendaylight.yangtools.yang.data.api;
    requires org.opendaylight.yangtools.yang.data.spi;
    requires org.opendaylight.yang.gen.ietf.subscribed.notifications.rfc8639;
    requires org.slf4j;

    // Annotations
    requires static transitive java.annotation;
    requires static transitive javax.inject;
    requires static transitive org.eclipse.jdt.annotation;
    requires static org.kohsuke.metainf_services;
    requires static org.osgi.service.component.annotations;
    requires static org.osgi.annotation.bundle;
}
