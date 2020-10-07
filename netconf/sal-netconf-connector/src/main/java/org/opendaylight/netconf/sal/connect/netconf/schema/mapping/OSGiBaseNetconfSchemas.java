/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.schema.mapping;

import com.google.common.annotations.Beta;
import org.opendaylight.yangtools.yang.model.parser.api.YangParserException;
import org.opendaylight.yangtools.yang.model.parser.api.YangParserFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Beta
@Component(immediate = true)
public final class OSGiBaseNetconfSchemas implements BaseNetconfSchemas {
    private static final Logger LOG = LoggerFactory.getLogger(OSGiBaseNetconfSchemas.class);

    @Reference
    YangParserFactory parserFactory;

    private DefaultBaseNetconfSchemas delegate;

    @Override
    public BaseSchema getBaseSchema() {
        return delegate.getBaseSchema();
    }

    @Override
    public BaseSchema getBaseSchemaWithNotifications() {
        return delegate.getBaseSchemaWithNotifications();
    }

    @Activate
    void activate() throws YangParserException {
        delegate = new DefaultBaseNetconfSchemas(parserFactory);
        LOG.info("Base NETCONF Schemas started");
    }

    @Deactivate
    void deactivate() {
        delegate = null;
        LOG.info("Base NETCONF Schemas stopped");
    }
}
