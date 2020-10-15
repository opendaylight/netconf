/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.dom;

import com.google.common.util.concurrent.FluentFuture;
import java.util.List;
import java.util.Optional;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * Grouped NETCONF DOM read operations that are using fields parameter for selective reading of data.
 */
public interface NetconfDOMFieldsReadOperations {

    /**
     * Reads only selected data field from provided logical data store located at the provided path.
     *
     * @param store  Logical data store from which read should occur.
     * @param path   Path which uniquely identifies subtree from which client wants to read selected fields.
     * @param fields List of relative paths under parent path which client wants to read.
     * @return FluentFuture containing the result of the read. Except the selected fields, the result may contain
     *     also next fields that are required to successfully build output {@link NormalizedNode}.
     */
    FluentFuture<Optional<NormalizedNode<?, ?>>> read(LogicalDatastoreType store, YangInstanceIdentifier path,
                                                      List<YangInstanceIdentifier> fields);
}