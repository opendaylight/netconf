/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.handlers.api;

import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;

/**
 * Handling schema context:
 * <ul>
 * <li>Retention
 * <li>Update
 * </ul>
 */
public interface SchemaContextHandler extends SchemaContextListener {

    /**
     * Get the {@link SchemaContext}.
     *
     * @return {@link SchemaContext}
     */
    SchemaContext getSchemaContext();
}
