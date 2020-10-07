/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.schema.mapping;

import com.google.common.annotations.Beta;
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.parser.api.YangParserException;
import org.opendaylight.yangtools.yang.model.parser.api.YangParserFactory;

@Beta
@Singleton
public final class DefaultBaseNetconfSchemas implements BaseNetconfSchemas {
    private final @NonNull BaseSchema withoutNotifications;
    private final @NonNull BaseSchema withNotifications;

    @Inject
    public DefaultBaseNetconfSchemas(final YangParserFactory parserFactory) throws YangParserException {
        withoutNotifications = new BaseSchema(withoutNotifications(parserFactory));
        withNotifications = new BaseSchema(withNotifications(parserFactory));
    }

    @Override
    public @NonNull BaseSchema getBaseSchema() {
        return withoutNotifications;
    }

    @Override
    public @NonNull BaseSchema getBaseSchemaWithNotifications() {
        return withNotifications;
    }

    private static EffectiveModelContext withoutNotifications(final YangParserFactory parserFactory)
            throws YangParserException {
        return BindingRuntimeHelpers.createEffectiveModel(parserFactory, Arrays.asList(
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601
                .$YangModuleInfoImpl.getInstance(),
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.extension.rev131210
                .$YangModuleInfoImpl.getInstance()));
    }

    private static EffectiveModelContext withNotifications(final YangParserFactory parserFactory)
            throws YangParserException {
        return BindingRuntimeHelpers.createEffectiveModel(parserFactory, Arrays.asList(
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.extension.rev131210
                .$YangModuleInfoImpl.getInstance(),
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714
                .$YangModuleInfoImpl.getInstance(),
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601
                .$YangModuleInfoImpl.getInstance(),
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206
                .$YangModuleInfoImpl.getInstance()));
    }
}
