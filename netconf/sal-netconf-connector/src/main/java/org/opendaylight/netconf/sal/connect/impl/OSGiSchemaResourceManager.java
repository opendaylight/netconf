/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.impl;

import com.google.common.annotations.Beta;
import org.opendaylight.netconf.sal.connect.api.SchemaResourceManager;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDevice.SchemaResourcesDTO;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yangtools.yang.model.parser.api.YangParserFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Beta
@Component(immediate = true)
//FIXME: merge with DefaultSchemaResourceManager when we have OSGi R7
public final class OSGiSchemaResourceManager implements SchemaResourceManager {
    private static final Logger LOG = LoggerFactory.getLogger(OSGiSchemaResourceManager.class);

    @Reference
    YangParserFactory parserFactory = null;

    private DefaultSchemaResourceManager delegate = null;

    @Override
    public SchemaResourcesDTO getSchemaResources(final NetconfNode node, final Object nodeId) {
        return delegate.getSchemaResources(node, nodeId);
    }

    @Activate
    void activate() {
        delegate = new DefaultSchemaResourceManager(parserFactory);
        LOG.info("Schema Resource Manager activated");
    }

    @Deactivate
    void deactivate() {
        delegate = null;
        LOG.info("Schema Resource Manager deactivated");
    }
}
