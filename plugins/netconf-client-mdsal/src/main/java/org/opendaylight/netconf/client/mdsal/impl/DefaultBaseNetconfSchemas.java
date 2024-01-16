/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static java.util.Objects.requireNonNull;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemas;
import org.opendaylight.yangtools.yang.parser.api.YangParserException;
import org.opendaylight.yangtools.yang.parser.api.YangParserFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component
@Singleton
@NonNullByDefault
public record DefaultBaseNetconfSchemas(BaseSchema baseSchema, BaseSchema baseSchemaWithNotifications)
        implements BaseNetconfSchemas {
    public DefaultBaseNetconfSchemas {
        requireNonNull(baseSchema);
        requireNonNull(baseSchemaWithNotifications);
    }

    @Inject
    @Activate
    public DefaultBaseNetconfSchemas(@Reference final YangParserFactory parserFactory) throws YangParserException {
        this(
            new BaseSchema(BindingRuntimeHelpers.createEffectiveModel(parserFactory, List.of(
                org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601
                    .YangModuleInfoImpl.getInstance(),
                org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004
                    .YangModuleInfoImpl.getInstance()))),
            new BaseSchema(BindingRuntimeHelpers.createEffectiveModel(parserFactory, List.of(
                org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714
                    .YangModuleInfoImpl.getInstance(),
                org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601
                    .YangModuleInfoImpl.getInstance(),
                org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004
                    .YangModuleInfoImpl.getInstance(),
                org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206
                    .YangModuleInfoImpl.getInstance()))));
    }
}
