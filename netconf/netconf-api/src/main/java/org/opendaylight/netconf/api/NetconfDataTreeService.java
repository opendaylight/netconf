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

    ListenableFuture<? extends DOMRpcResult> editConfigMerge(LogicalDatastoreType store, YangInstanceIdentifier path,
                                                             NormalizedNode<?, ?> data,
                                                             Optional<ModifyAction> defaultOperation);

    ListenableFuture<? extends DOMRpcResult> editConfigReplace(LogicalDatastoreType store, YangInstanceIdentifier path,
                                                               NormalizedNode<?, ?> data,
                                                               Optional<ModifyAction> defaultOperation);

    ListenableFuture<? extends DOMRpcResult> editConfigCreate(LogicalDatastoreType store, YangInstanceIdentifier path,
                                                              NormalizedNode<?, ?> data,
                                                              Optional<ModifyAction> defaultOperation);

    ListenableFuture<? extends DOMRpcResult> editConfigDelete(LogicalDatastoreType store, YangInstanceIdentifier path,
                                                              NormalizedNode<?, ?> data,
                                                              Optional<ModifyAction> defaultOperation);

    ListenableFuture<? extends DOMRpcResult> editConfigRemove(LogicalDatastoreType store, YangInstanceIdentifier path,
                                                              NormalizedNode<?, ?> data,
                                                              Optional<ModifyAction> defaultOperation);
}