/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf;

import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.core.Application;
import org.opendaylight.netconf.md.sal.rest.schema.SchemaExportContentYangBodyWriter;
import org.opendaylight.netconf.md.sal.rest.schema.SchemaExportContentYinBodyWriter;
import org.opendaylight.netconf.sal.rest.impl.NormalizedNodeJsonBodyWriter;
import org.opendaylight.netconf.sal.rest.impl.NormalizedNodeXmlBodyWriter;
import org.opendaylight.restconf.common.handlers.api.SchemaContextHandler;
import org.opendaylight.restconf.common.handlers.impl.SchemaContextHandlerImpl;
import org.opendaylight.restconf.rest.handlers.api.DOMMountPointServiceHandler;
import org.opendaylight.restconf.rest.handlers.impl.DOMMountPointServiceHandlerImpl;
import org.opendaylight.restconf.rest.services.impl.Draft11ServicesWrapperImpl;
import org.osgi.framework.FrameworkUtil;

public class RestconfApplication extends Application implements RestconfApplicationService {

    private final SchemaContextHandler schemaContextHandler;
    private final DOMMountPointServiceHandler domMountPointServiceHandler;

    public RestconfApplication() {
        this.schemaContextHandler = new SchemaContextHandlerImpl();
        this.domMountPointServiceHandler = new DOMMountPointServiceHandlerImpl();
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
        singletons.add(this.schemaContextHandler);
        singletons.add(new Draft11ServicesWrapperImpl(this.schemaContextHandler, this.domMountPointServiceHandler));
        return singletons;
    }

    @Override
    public SchemaContextHandler getSchemaContextHandler() {
        return this.schemaContextHandler;
    }

    @Override
    public DOMMountPointServiceHandler getDOMMountPointServiceHandler() {
        return this.domMountPointServiceHandler;
    }
}
