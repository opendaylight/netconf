/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.mdsal;

import dagger.Component;
import jakarta.inject.Singleton;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.NotificationPublishService;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.broker.DOMNotificationRouter;
import org.opendaylight.mdsal.dom.broker.DOMRpcRouter;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipService;
import org.opendaylight.mdsal.singleton.api.ClusterSingletonServiceProvider;
import org.opendaylight.netconf.dagger.controller.SalDistributedDatastoreModule;
import org.opendaylight.netconf.dagger.mdsal.MdsalQualifiers.SchemaServiceContext;
import org.opendaylight.netconf.dagger.springboot.config.SpringbootConfigLoaderModule;
import org.opendaylight.odlparent.dagger.AutoCloseableComponent;
import org.opendaylight.odlparent.dagger.ResourceSupportModule;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

@Singleton
@NonNullByDefault
@Component(modules = {
    MdsalDomBrokerModule.class,
    MdsalBindingDomAdapterModule.class,
    MdsalEosBindingAdapterModule.class,
    MdsalEosDomSimpleModule.class,
    MdsalSingletonImplModule.class,
    SalDistributedDatastoreModule.class,
    SpringbootConfigLoaderModule.class,
    ResourceSupportModule.class
})
public interface MdsalDomBrokerTestFactory extends AutoCloseableComponent {

    DOMNotificationRouter domNotificationRouter();

    DOMActionService domActionService();

    DOMRpcRouter domRpcRouter();

    NotificationPublishService notificationPublishService();

    @SchemaServiceContext EffectiveModelContext modelContext();

    ClusterSingletonServiceProvider clusterSingletonServiceProvider();

    EntityOwnershipService entityOwnershipService();

    DataBroker dataBroker();
}
