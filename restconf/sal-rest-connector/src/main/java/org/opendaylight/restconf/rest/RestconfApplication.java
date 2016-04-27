/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest;

import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.core.Application;
import org.opendaylight.netconf.md.sal.rest.schema.SchemaExportContentYangBodyWriter;
import org.opendaylight.netconf.md.sal.rest.schema.SchemaExportContentYinBodyWriter;
import org.opendaylight.netconf.sal.rest.impl.NormalizedNodeJsonBodyWriter;
import org.opendaylight.netconf.sal.rest.impl.NormalizedNodeXmlBodyWriter;
import org.opendaylight.restconf.rest.api.schema.context.SchemaContextHandler;
import org.opendaylight.restconf.rest.impl.schema.context.SchemaContextHandlerImpl;
import org.opendaylight.restconf.rest.impl.services.Draft11ServicesWrapperImpl;
import org.osgi.framework.FrameworkUtil;

public class RestconfApplication extends Application implements RestconfApplicationService {

    private final SchemaContextHandler schemaController;

    public RestconfApplication() {
        this.schemaController = new SchemaContextHandlerImpl();
        FrameworkUtil.getBundle(getClass()).getBundleContext().registerService(RestconfApplicationService.class.getName(),
                this, null);
    }

    @Override
    public Set<Class<?>> getClasses() {
        return ImmutableSet.<Class<?>> builder().add(NormalizedNodeJsonBodyWriter.class)
                .add(NormalizedNodeXmlBodyWriter.class).add(SchemaExportContentYinBodyWriter.class)
                .add(SchemaExportContentYangBodyWriter.class)
                .build();
    }

    @Override
    public Set<Object> getSingletons() {
        final Set<Object> singletons = new HashSet<>();
        singletons.add(this.schemaController);
        singletons.add(new Draft11ServicesWrapperImpl(this.schemaController));
        return singletons;
    }

    @Override
    public SchemaContextHandler getRestConnector() {
        return this.schemaController;
    }
}
