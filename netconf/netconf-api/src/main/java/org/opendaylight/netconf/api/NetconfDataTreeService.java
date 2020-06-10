/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Optional;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMService;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public interface NetconfDataTreeService extends DOMService {

    FluentFuture<Optional<NormalizedNode<?,?>>> getConfig(LogicalDatastoreType store, YangInstanceIdentifier path);

    FluentFuture<Boolean> exists(LogicalDatastoreType store, YangInstanceIdentifier path);

    ListenableFuture<? extends DOMRpcResult> merge(LogicalDatastoreType store, YangInstanceIdentifier path,
                                                   NormalizedNode<?, ?> data,
                                                   Optional<ModifyAction> defaultOperation);

    ListenableFuture<? extends DOMRpcResult> replace(LogicalDatastoreType store, YangInstanceIdentifier path,
                                                     NormalizedNode<?, ?> data,
                                                     Optional<ModifyAction> defaultOperation);

    ListenableFuture<? extends DOMRpcResult> create(LogicalDatastoreType store, YangInstanceIdentifier path,
                                                    NormalizedNode<?, ?> data,
                                                    Optional<ModifyAction> defaultOperation);

    ListenableFuture<? extends DOMRpcResult> delete(LogicalDatastoreType store, YangInstanceIdentifier path,
                                                    NormalizedNode<?, ?> data,
                                                    Optional<ModifyAction> defaultOperation);

    ListenableFuture<? extends DOMRpcResult> remove(LogicalDatastoreType store, YangInstanceIdentifier path,
                                                    NormalizedNode<?, ?> data,
                                                    Optional<ModifyAction> defaultOperation);
}