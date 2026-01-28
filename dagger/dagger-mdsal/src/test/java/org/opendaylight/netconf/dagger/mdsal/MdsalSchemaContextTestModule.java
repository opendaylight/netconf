/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.mdsal;

import dagger.Module;
import dagger.Provides;
import org.opendaylight.netconf.dagger.mdsal.MdsalQualifiers.SchemaServiceContext;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.IetfNetconfData;
import org.opendaylight.yangtools.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

@Module
abstract class MdsalSchemaContextTestModule {

    @Provides
    static @SchemaServiceContext EffectiveModelContext effectiveModel() {
        return BindingRuntimeHelpers.createEffectiveModel(IetfNetconfData.class);
    }
}
