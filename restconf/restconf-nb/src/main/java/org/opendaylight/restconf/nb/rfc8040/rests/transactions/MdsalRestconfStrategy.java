/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Optional;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService.YangTextSourceExtension;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.common.errors.SettableRestconfFuture;
import org.opendaylight.restconf.nb.rfc8040.legacy.QueryParameters;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.WriterFieldsTranslator;
import org.opendaylight.restconf.server.api.DataGetParams;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.spi.ApiPathNormalizer.DataPath;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * Implementation of RESTCONF operations using {@link DOMTransactionChain} and related concepts.
 *
 * @see DOMTransactionChain
 * @see DOMDataTreeReadWriteTransaction
 */
public final class MdsalRestconfStrategy extends RestconfStrategy {
    private final DOMDataBroker dataBroker;

    public MdsalRestconfStrategy(final DatabindContext databind, final DOMDataBroker dataBroker,
            final @Nullable DOMRpcService rpcService, final @Nullable DOMActionService actionService,
            final @Nullable YangTextSourceExtension sourceProvider,
            final @Nullable DOMMountPointService mountPointService,
            final ImmutableMap<QName, RpcImplementation> localRpcs) {
        super(databind, localRpcs, rpcService, actionService, sourceProvider, mountPointService);
        this.dataBroker = requireNonNull(dataBroker);
    }

    public MdsalRestconfStrategy(final DatabindContext databind, final DOMDataBroker dataBroker,
            final @Nullable DOMRpcService rpcService, final @Nullable DOMActionService actionService,
            final @Nullable YangTextSourceExtension sourceProvider,
            final @Nullable DOMMountPointService mountPointService) {
        this(databind, dataBroker, rpcService, actionService, sourceProvider, mountPointService, ImmutableMap.of());
    }

    @Override
    RestconfTransaction prepareWriteExecution() {
        return new MdsalRestconfTransaction(modelContext(), dataBroker);
    }

    @Override
    void delete(final SettableRestconfFuture<Empty> future, final YangInstanceIdentifier path) {
        final var tx = dataBroker.newReadWriteTransaction();
        tx.exists(CONFIGURATION, path).addCallback(new FutureCallback<>() {
            @Override
            public void onSuccess(final Boolean result) {
                if (!result) {
                    cancelTx(new RestconfDocumentedException("Data does not exist", ErrorType.PROTOCOL,
                        ErrorTag.DATA_MISSING, path));
                    return;
                }

                tx.delete(CONFIGURATION, path);
                tx.commit().addCallback(new FutureCallback<CommitInfo>() {
                    @Override
                    public void onSuccess(final CommitInfo result) {
                        future.set(Empty.value());
                    }

                    @Override
                    public void onFailure(final Throwable cause) {
                        future.setFailure(new RestconfDocumentedException("Transaction to delete " + path + " failed",
                            cause));
                    }
                }, MoreExecutors.directExecutor());
            }

            @Override
            public void onFailure(final Throwable cause) {
                cancelTx(new RestconfDocumentedException("Failed to access " + path, cause));
            }

            private void cancelTx(final RestconfDocumentedException ex) {
                tx.cancel();
                future.setFailure(ex);
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    RestconfFuture<DataGetResult> dataGET(final DataPath path, final DataGetParams params) {
        final var inference = path.inference();
        final var fields = params.fields();
        final var translatedFields = fields == null ? null
            : WriterFieldsTranslator.translate(inference.modelContext(), path.schema(), fields);
        return completeDataGET(inference, QueryParameters.of(params, translatedFields),
            readData(params.content(), path.instance(), params.withDefaults()), null);
    }

    @Override
    ListenableFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        try (var tx = dataBroker.newReadOnlyTransaction()) {
            return tx.read(store, path);
        }
    }

    @Override
    ListenableFuture<Boolean> exists(final YangInstanceIdentifier path) {
        try (var tx = dataBroker.newReadOnlyTransaction()) {
            return tx.exists(LogicalDatastoreType.CONFIGURATION, path);
        }
    }
}
