/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import com.google.common.annotations.Beta;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemas;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.parser.api.YangParserException;
import org.opendaylight.yangtools.yang.parser.api.YangParserFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Beta
@Component
@Singleton
public final class DefaultBaseNetconfSchemas implements BaseNetconfSchemas {
    private final @NonNull BaseSchema withoutNotifications;
    private final @NonNull BaseSchema withNotifications;

    @Inject
    @Activate
    public DefaultBaseNetconfSchemas(@Reference final YangParserFactory parserFactory) throws YangParserException {
        withoutNotifications = new BaseSchema(withoutNotifications(parserFactory));
        withNotifications = new BaseSchema(withNotifications(parserFactory));
    }

    @Override
    public BaseSchema getBaseSchema() {
        return withoutNotifications;
    }

    @Override
    public BaseSchema getBaseSchemaWithNotifications() {
        return withNotifications;
    }

    private static EffectiveModelContext withoutNotifications(final YangParserFactory parserFactory)
            throws YangParserException {
        return BindingRuntimeHelpers.createEffectiveModel(parserFactory, List.of(
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601
                .$YangModuleInfoImpl.getInstance(),
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004
                .$YangModuleInfoImpl.getInstance()));
    }

    private static EffectiveModelContext withNotifications(final YangParserFactory parserFactory)
            throws YangParserException {
        return BindingRuntimeHelpers.createEffectiveModel(parserFactory, List.of(
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714
                .$YangModuleInfoImpl.getInstance(),
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601
                .$YangModuleInfoImpl.getInstance(),
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004
                .$YangModuleInfoImpl.getInstance(),
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206
                .$YangModuleInfoImpl.getInstance()));
    }
}
