/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.api;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collection;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.restconfsb.communicator.api.http.HttpException;
import org.opendaylight.restconfsb.communicator.api.stream.RestconfDeviceStreamListener;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public interface RestconfFacade extends AutoCloseable {
    ListenableFuture<Void> headData(LogicalDatastoreType datastore, YangInstanceIdentifier path);

    ListenableFuture<Optional<NormalizedNode<?, ?>>> getData(LogicalDatastoreType datastore, YangInstanceIdentifier path);

    ListenableFuture<Optional<NormalizedNode<?, ?>>> postOperation(SchemaPath type, ContainerNode input);

    ListenableFuture<Void> postConfig(YangInstanceIdentifier path, NormalizedNode<?, ?> input);

    ListenableFuture<Void> putConfig(YangInstanceIdentifier path, NormalizedNode<?, ?> input);

    ListenableFuture<Void> patchConfig(YangInstanceIdentifier path, NormalizedNode<?, ?> input);

    ListenableFuture<Void> deleteConfig(YangInstanceIdentifier path);

    void registerNotificationListener(RestconfDeviceStreamListener listener);

    /**
     * Tries to parse http error message to {@link RpcError}. If it is not possible, returns generic RpcError with
     * exception message.
     * @param exception HTTP exception
     * @return rpc errors
     */
    Collection<RpcError> parseErrors(HttpException exception);
}
