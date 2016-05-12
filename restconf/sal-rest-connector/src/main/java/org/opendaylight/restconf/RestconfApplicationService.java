/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf;

import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.common.handlers.api.SchemaContextHandler;
import org.opendaylight.restconf.rest.handlers.api.DOMMountPointServiceHandler;
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

    /**
     * Get {@link DOMMountPointServiceHandler} via service. Actually use by
     * {@link RestConnectorProvider} to set {@link DOMMountPointService}.
     *
     * @return {@link DOMMountPointServiceHandler}
     */
    DOMMountPointServiceHandler getDOMMountPointServiceHandler();
}
