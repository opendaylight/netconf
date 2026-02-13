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
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.broker.DOMNotificationRouter;
import org.opendaylight.mdsal.dom.broker.DOMRpcRouter;
import org.opendaylight.netconf.dagger.smallrye.config.SmallRyeConfigLoaderModule;
import org.opendaylight.odlparent.dagger.AutoCloseableComponent;
import org.opendaylight.odlparent.dagger.ResourceSupportModule;

@Singleton
@NonNullByDefault
@Component(modules = {
    MdsalDomBrokerModule.class,
    MdsalSchemaContextTestModule.class,
    SmallRyeConfigLoaderModule.class,
    ResourceSupportModule.class
})
public interface MdsalDomBrokerTestFactory extends AutoCloseableComponent {

    DOMNotificationRouter domNotificationRouter();

    DOMActionService domActionService();

    DOMRpcRouter domRpcRouter();
}
