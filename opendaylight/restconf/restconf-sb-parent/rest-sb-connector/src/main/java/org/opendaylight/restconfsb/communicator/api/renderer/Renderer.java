/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.api.renderer;

import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.restconfsb.communicator.api.http.Request;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

/**
 * Renders binding independent data to form accepted by restconf server.
 */
public interface Renderer {

    /**
     * Renders request for reading specified path
     * @param path path
     * @param type datastore
     * @return request
     */
    Request renderGetData(final YangInstanceIdentifier path, final LogicalDatastoreType type);

    /**
     * Renders request for editing config data on specified path
     * @param path path
     * @param body config data
     * @return request
     */
    Request renderEditConfig(final YangInstanceIdentifier path, final NormalizedNode<?, ?> body);

    /**
     * Renders request for deleting config data on specified path
     * @param path path
     * @return request
     */
    Request renderDeleteConfig(final YangInstanceIdentifier path);

    /**
     * Renders request for invoking operation specified by type
     * @param type operation type
     * @param input operation input
     * @return request
     */
    Request renderOperation(final SchemaPath type, final ContainerNode input);
}