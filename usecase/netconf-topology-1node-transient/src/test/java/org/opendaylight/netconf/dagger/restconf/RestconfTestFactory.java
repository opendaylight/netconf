/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.restconf;

import dagger.Component;
import jakarta.inject.Singleton;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.dagger.aaa.InsecureAAAModule;
import org.opendaylight.netconf.dagger.controller.DOMDataBrokerModule;
import org.opendaylight.netconf.dagger.mdsal.InMemoryDataStoreModule;
import org.opendaylight.netconf.dagger.mdsal.MdsalBindingDomAdapterModule;
import org.opendaylight.netconf.dagger.mdsal.MdsalDomBrokerModule;
import org.opendaylight.netconf.dagger.mdsal.MdsalEosBindingAdapterModule;
import org.opendaylight.netconf.dagger.mdsal.MdsalEosDomSimpleModule;
import org.opendaylight.netconf.dagger.mdsal.MdsalSingletonImplModule;
import org.opendaylight.netconf.dagger.netconf.RestconfModule;
import org.opendaylight.netconf.dagger.springboot.config.SpringbootConfigLoaderModule;
import org.opendaylight.odlparent.dagger.AutoCloseableComponent;
import org.opendaylight.odlparent.dagger.ResourceSupportModule;
import org.opendaylight.restconf.server.NettyEndpoint;

@Singleton
@NonNullByDefault
@Component(modules = {
    MdsalDomBrokerModule.class,
    MdsalBindingDomAdapterModule.class,
    MdsalEosBindingAdapterModule.class,
    MdsalEosDomSimpleModule.class,
    MdsalSingletonImplModule.class,
    DOMDataBrokerModule.class,
    InMemoryDataStoreModule.class,
    InsecureAAAModule.class,
    RestconfModule.class,
    SpringbootConfigLoaderModule.class,
    ResourceSupportModule.class
})
public interface RestconfTestFactory extends AutoCloseableComponent {

    NettyEndpoint nettyEndpoint();
}
