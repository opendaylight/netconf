/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
import org.opendaylight.netconf.rfc8639.impl.IetfSubscriptionFeatureProvider;
import org.opendaylight.yangtools.binding.meta.YangFeatureProvider;

/**
 * RPCs implementing {@code ietf-subscribed-notifications.yang}.
 */
module org.opendaylight.netconf.rfc8639 {
    exports org.opendaylight.netconf.rfc8639;

    provides YangFeatureProvider with IetfSubscriptionFeatureProvider;

    requires transitive org.opendaylight.restconf.server.spi;
    requires transitive org.opendaylight.yangtools.concepts;
    requires transitive org.opendaylight.yangtools.yang.common;
    requires org.opendaylight.yangtools.binding.spec;
    requires org.opendaylight.yangtools.yang.data.api;
    requires org.opendaylight.yangtools.yang.data.spi;
    requires org.opendaylight.yang.gen.ietf.subscribed.notifications.rfc8639;
    requires org.slf4j;

    // Annotations
    requires static transitive javax.inject;
    requires static transitive org.eclipse.jdt.annotation;
    requires static org.kohsuke.metainf_services;
    requires static org.osgi.service.component.annotations;
    requires static org.osgi.annotation.bundle;
}
