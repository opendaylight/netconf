/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest;

import org.opendaylight.restconf.rest.api.schema.context.SchemaContextHandler;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;
import org.osgi.framework.BundleContext;

/**
 * Interface for register RestconfApplication service via {@link BundleContext}.
 *
 */
public interface RestconfApplicationService {

    /**
     * Get {@link SchemaContextHandler} via service. Actually use by
     * {@link RestConnectorProvider} to register {@link SchemaContextListener}
     * of {@link SchemaContextHandler}.
     *
     * @return {@link SchemaContextHandler}
     */
    SchemaContextHandler getSchemaContextHandler();
}
