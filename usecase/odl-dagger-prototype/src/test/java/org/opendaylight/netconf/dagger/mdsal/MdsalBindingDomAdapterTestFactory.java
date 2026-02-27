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
import org.opendaylight.mdsal.binding.api.NotificationPublishService;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.netconf.dagger.mdsal.MdsalQualifiers.SchemaServiceContext;
import org.opendaylight.netconf.dagger.springboot.config.SpringbootConfigLoaderModule;
import org.opendaylight.odlparent.dagger.AutoCloseableComponent;
import org.opendaylight.odlparent.dagger.ResourceSupportModule;
import org.opendaylight.yangtools.binding.generator.dagger.BindingRuntimeGeneratorModule;
import org.opendaylight.yangtools.odlext.parser.dagger.OdlCodegenModule;
import org.opendaylight.yangtools.odlext.parser.dagger.YangExtModule;
import org.opendaylight.yangtools.openconfig.parser.dagger.OpenConfigModule;
import org.opendaylight.yangtools.rfc6241.parser.dagger.Rfc6241Module;
import org.opendaylight.yangtools.rfc6536.parser.dagger.Rfc6536Module;
import org.opendaylight.yangtools.rfc6643.parser.dagger.Rfc6643Module;
import org.opendaylight.yangtools.rfc7952.parser.dagger.Rfc7952Module;
import org.opendaylight.yangtools.rfc8040.parser.dagger.Rfc8040Module;
import org.opendaylight.yangtools.rfc8528.parser.dagger.Rfc8528Module;
import org.opendaylight.yangtools.rfc8639.parser.dagger.Rfc8639Module;
import org.opendaylight.yangtools.rfc8819.parser.dagger.Rfc8819Module;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.parser.dagger.YangLibResolverModule;
import org.opendaylight.yangtools.yang.parser.dagger.YangParserFactoryModule;
import org.opendaylight.yangtools.yang.xpath.dagger.YangXPathParserFactoryModule;

@Singleton
@NonNullByDefault
@Component(modules = {
    MdsalDomBrokerModule.class,
    MdsalBindingDomAdapter.class,
    SpringbootConfigLoaderModule.class,
    BindingRuntimeGeneratorModule.class,
    YangXPathParserFactoryModule.class,
    YangParserFactoryModule.class,
    YangLibResolverModule.class,
    Rfc6241Module.class,
    Rfc6536Module.class,
    Rfc6643Module.class,
    Rfc7952Module.class,
    Rfc8040Module.class,
    Rfc8528Module.class,
    Rfc8639Module.class,
    Rfc8819Module.class,
    OdlCodegenModule.class,
    YangExtModule.class,
    OpenConfigModule.class,
    ResourceSupportModule.class
})
public interface MdsalBindingDomAdapterTestFactory extends AutoCloseableComponent  {

    DOMActionService domActionService();

    NotificationPublishService notificationPublishService();

    @SchemaServiceContext EffectiveModelContext modelContext();
}
