/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import com.google.common.util.concurrent.FluentFuture;
import java.util.Optional;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.xpath.NetconfXPathContext;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * Allows to use GET and GET-CONFIG operations with XPath filter according to
 * the RFC 6241. Netconf device must support XPath capability.
 */
public interface NetconfReadOnlyTransaction extends DOMDataTreeReadTransaction {

    /**
     * Reads data from provided logical data store located at the provided XPath.
     *
     * @param store        Logical data store from which read should occur.
     * @param xpathContext Context which identifies XPath which client wants to read
     * @param path         Define parent of specific nodes which client wants to
     *                     read or top node and allows to transform data from
     *                     response back to {@link NormalizedNode}
     * @return FluentFuture containing the result of the read. The Future blocks
     *         until the commit operation is complete.
     */
    default FluentFuture<Optional<NormalizedNode<?, ?>>> read(LogicalDatastoreType store,
            NetconfXPathContext xpathContext, YangInstanceIdentifier path) {
        throw new UnsupportedOperationException("Not supported");
    }
}
