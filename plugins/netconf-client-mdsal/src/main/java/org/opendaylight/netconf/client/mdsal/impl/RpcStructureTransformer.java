/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import java.util.List;
import java.util.Optional;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * Transforms rpc structures to normalized nodes and vice versa.
 */
interface RpcStructureTransformer {
    /**
     * Transforms data and path to the config element structure. It means creating of parent xml structure
     * specified by path and appending data to the structure. Operation is set as attribute on data element.
     *
     * @param data data
     * @param dataPath path, where data will be written
     * @param operation operation
     * @return config structure
     */
    DOMSourceAnyxmlNode createEditConfigStructure(Optional<NormalizedNode> data, YangInstanceIdentifier dataPath,
                                                  Optional<EffectiveOperation> operation);

    /**
     * Transforms path to filter structure.
     *
     * @param path path
     * @return filter structure
     */
    AnyxmlNode<?> toFilterStructure(YangInstanceIdentifier path);

    /**
     * Transforms list of fields filters to filter structure.
     * Field paths are relative to parent query path.
     *
     * @param fieldsFilters list of: parent path and selection fields
     * @return filter structure
     */
    AnyxmlNode<?> toFilterStructure(List<FieldsFilter> fieldsFilters);

    /**
     * Selects data specified by path from data node. Data must be product of get-config rpc with filter created by
     * {@link #toFilterStructure(YangInstanceIdentifier)} or {@link #toFilterStructure(List)} )} with same path.
     *
     * @param data data
     * @param path path to select
     * @return selected data
     */
    Optional<NormalizedNode> selectFromDataStructure(DataContainerChild data, YangInstanceIdentifier path);
}
