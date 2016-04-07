/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest;

import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.core.Application;
import org.opendaylight.restconf.rest.api.connector.RestSchemaController;
import org.opendaylight.restconf.rest.impl.connector.RestSchemaControllerImpl;
import org.opendaylight.restconf.rest.impl.services.ServicesWrapperImpl;
import org.opendaylight.restconf.rest.providers.NormalizedNodeJsonBodyWriter;
import org.opendaylight.restconf.rest.providers.NormalizedNodeXmlBodyWriter;
import org.opendaylight.restconf.rest.providers.schema.SchemaYangBodyWriter;
import org.opendaylight.restconf.rest.providers.schema.SchemaYinBodyWriter;
import org.osgi.framework.FrameworkUtil;
import com.google.common.collect.ImmutableSet;

public class RestconfApplication extends Application implements RestconfApplicationService {

    private final RestSchemaController restSchemaController;

    public RestconfApplication() {
        this.restSchemaController = new RestSchemaControllerImpl();
        FrameworkUtil.getBundle(getClass()).getBundleContext().registerService(RestconfApplicationService.class.getName(),
                this, null);
    }

    @Override
    public Set<Class<?>> getClasses() {
        return ImmutableSet.<Class<?>> builder().add(NormalizedNodeJsonBodyWriter.class)
                .add(NormalizedNodeXmlBodyWriter.class).add(SchemaYinBodyWriter.class).add(SchemaYangBodyWriter.class)
                .build();
    }

    @Override
    public Set<Object> getSingletons() {
        final Set<Object> singletons = new HashSet<>();
        singletons.add(this.restSchemaController);
        singletons.add(new ServicesWrapperImpl(this.restSchemaController));
        return singletons;
    }

    @Override
    public RestSchemaController getRestConnector() {
        return this.restSchemaController;
    }
}
