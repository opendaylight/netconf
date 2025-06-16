/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.Optional;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public interface DataStoreService {

    ListenableFuture<? extends DOMRpcResult> commit();

    ListenableFuture<? extends DOMRpcResult> cancel();

    ListenableFuture<? extends DOMRpcResult> create(YangInstanceIdentifier path, NormalizedNode data);

    ListenableFuture<? extends DOMRpcResult> delete(YangInstanceIdentifier path);

    ListenableFuture<? extends DOMRpcResult> remove(YangInstanceIdentifier path);

    ListenableFuture<? extends DOMRpcResult> merge(YangInstanceIdentifier path, NormalizedNode data);

    ListenableFuture<? extends DOMRpcResult> put(YangInstanceIdentifier path, NormalizedNode data);

    ListenableFuture<Optional<NormalizedNode>> read(LogicalDatastoreType store, YangInstanceIdentifier path,
        List<YangInstanceIdentifier> fields);
}
