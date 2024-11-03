/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService.YangTextSourceExtension;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.common.errors.SettableRestconfFuture;
import org.opendaylight.restconf.server.api.DataGetParams;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.api.DatabindPath.Data;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * A {@link RestconfStrategy} which does not provide {@code /data} operations.
 */
public final class NoDataRestconfStrategy extends RestconfStrategy {
    private static final @NonNull RestconfError NOT_IMPLEMENTED =
        new RestconfError(ErrorType.PROTOCOL, ErrorTag.OPERATION_NOT_SUPPORTED, "Data request not supported");

    public NoDataRestconfStrategy(final DatabindContext databind,
            final ImmutableMap<QName, RpcImplementation> localRpcs, final DOMRpcService rpcService,
            final DOMActionService actionService, final YangTextSourceExtension sourceProvider,
            final DOMMountPointService mountPointService) {
        super(databind, localRpcs, rpcService, actionService, sourceProvider, mountPointService);
    }

    @Override
    RestconfTransaction prepareWriteExecution() {
        throw notImplemented();
    }

    @Override
    ListenableFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        return Futures.immediateFailedFuture(notImplemented());
    }

    @Override
    ListenableFuture<Boolean> exists(final YangInstanceIdentifier path) {
        return Futures.immediateFailedFuture(notImplemented());
    }

    @Override
    void delete(final SettableRestconfFuture<Empty> future, final ServerRequest request,
            final YangInstanceIdentifier path) {
        future.setFailure(notImplemented());
    }

    @Override
    RestconfFuture<DataGetResult> dataGET(final ServerRequest request, final Data path, final DataGetParams params) {
        return RestconfFuture.failed(notImplemented());
    }

    private static @NonNull RestconfDocumentedException notImplemented() {
        return new RestconfDocumentedException(NOT_IMPLEMENTED);
    }
}
