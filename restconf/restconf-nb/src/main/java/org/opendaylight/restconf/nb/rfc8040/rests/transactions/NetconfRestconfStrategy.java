/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.util.List;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService.YangTextSourceExtension;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.api.query.WithDefaultsParam;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.common.errors.SettableRestconfFuture;
import org.opendaylight.restconf.nb.rfc8040.legacy.QueryParameters;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.NetconfFieldsTranslator;
import org.opendaylight.restconf.server.api.DataGetParams;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.spi.ApiPathNormalizer.DataPath;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * Implementation of RESTCONF operations on top of a raw NETCONF backend.
 *
 * @see NetconfDataTreeService
 */
public final class NetconfRestconfStrategy extends RestconfStrategy {
    private final NetconfDataTreeService netconfService;

    public NetconfRestconfStrategy(final DatabindContext databind, final NetconfDataTreeService netconfService,
            final @Nullable DOMRpcService rpcService, final @Nullable DOMActionService actionService,
            final @Nullable YangTextSourceExtension sourceProvider,
            final @Nullable DOMMountPointService mountPointService) {
        super(databind, ImmutableMap.of(), rpcService, actionService, sourceProvider, mountPointService);
        this.netconfService = requireNonNull(netconfService);
    }

    @Override
    RestconfTransaction prepareWriteExecution() {
        return new NetconfRestconfTransaction(modelContext(), netconfService);
    }

    @Override
    void delete(final SettableRestconfFuture<Empty> future, final YangInstanceIdentifier path) {
        final var tx = prepareWriteExecution();
        tx.delete(path);
        Futures.addCallback(tx.commit(), new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                future.set(Empty.value());
            }

            @Override
            public void onFailure(final Throwable cause) {
                future.setFailure(TransactionUtil.decodeException(cause, "DELETE", path, modelContext()));
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    RestconfFuture<DataGetResult> dataGET(final DataPath path, final DataGetParams params) {
        final var inference = path.inference();
        final var fields = params.fields();
        final List<YangInstanceIdentifier> fieldPaths;
        if (fields != null) {
            final List<YangInstanceIdentifier> tmp;
            try {
                tmp = NetconfFieldsTranslator.translate(inference.modelContext(), path.schema(), fields);
            } catch (RestconfDocumentedException e) {
                return RestconfFuture.failed(e);
            }
            fieldPaths = tmp == null || tmp.isEmpty() ? null : tmp;
        } else {
            fieldPaths = null;
        }

        final NormalizedNode node;
        if (fieldPaths != null) {
            node = readData(params.content(), path.instance(), params.withDefaults(), fieldPaths);
        } else {
            node = readData(params.content(), path.instance(), params.withDefaults());
        }
        return completeDataGET(inference, QueryParameters.of(params), node, null);
    }

    @Override
    ListenableFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        return switch (store) {
            case CONFIGURATION -> netconfService.getConfig(path);
            case OPERATIONAL -> netconfService.get(path);
        };
    }

    private ListenableFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store,
            final YangInstanceIdentifier path, final List<YangInstanceIdentifier> fields) {
        return switch (store) {
            case CONFIGURATION -> netconfService.getConfig(path, fields);
            case OPERATIONAL -> netconfService.get(path, fields);
        };
    }

    /**
     * Read specific type of data from data store via transaction with specified subtrees that should only be read.
     * Close {@link DOMTransactionChain} inside of object {@link RestconfStrategy} provided as a parameter.
     *
     * @param content  type of data to read (config, state, all)
     * @param path     the parent path to read
     * @param withDefa value of with-defaults parameter
     * @param fields   paths to selected subtrees which should be read, relative to to the parent path
     * @return {@link NormalizedNode}
     */
    // FIXME: NETCONF-1155: this method should asynchronous
    public @Nullable NormalizedNode readData(final @NonNull ContentParam content,
            final @NonNull YangInstanceIdentifier path, final @Nullable WithDefaultsParam withDefa,
            final @NonNull List<YangInstanceIdentifier> fields) {
        return switch (content) {
            case ALL -> {
                // PREPARE STATE DATA NODE
                final var stateDataNode = readDataViaTransaction(LogicalDatastoreType.OPERATIONAL, path, fields);
                // PREPARE CONFIG DATA NODE
                final var configDataNode = readDataViaTransaction(LogicalDatastoreType.CONFIGURATION, path, fields);

                yield mergeConfigAndSTateDataIfNeeded(stateDataNode, withDefa == null ? configDataNode
                    : prepareDataByParamWithDef(configDataNode, path, withDefa.mode()));
            }
            case CONFIG -> {
                final var read = readDataViaTransaction(LogicalDatastoreType.CONFIGURATION, path, fields);
                yield withDefa == null ? read : prepareDataByParamWithDef(read, path, withDefa.mode());
            }
            case NONCONFIG -> readDataViaTransaction(LogicalDatastoreType.OPERATIONAL, path, fields);
        };
    }

    /**
     * Read specific type of data {@link LogicalDatastoreType} via transaction in {@link RestconfStrategy} with
     * specified subtrees that should only be read.
     *
     * @param store                 datastore type
     * @param path                  parent path to selected fields
     * @param closeTransactionChain if it is set to {@code true}, after transaction it will close transactionChain
     *                              in {@link RestconfStrategy} if any
     * @param fields                paths to selected subtrees which should be read, relative to to the parent path
     * @return {@link NormalizedNode}
     */
    private @Nullable NormalizedNode readDataViaTransaction(final @NonNull LogicalDatastoreType store,
            final @NonNull YangInstanceIdentifier path, final @NonNull List<YangInstanceIdentifier> fields) {
        return TransactionUtil.syncAccess(read(store, path, fields), path).orElse(null);
    }

    @Override
    ListenableFuture<Boolean> exists(final YangInstanceIdentifier path) {
        return Futures.transform(remapException(netconfService.getConfig(path)),
            optionalNode -> optionalNode != null && optionalNode.isPresent(),
            MoreExecutors.directExecutor());
    }

    private static <T> ListenableFuture<T> remapException(final ListenableFuture<T> input) {
        final var ret = SettableFuture.<T>create();
        Futures.addCallback(input, new FutureCallback<T>() {
            @Override
            public void onSuccess(final T result) {
                ret.set(result);
            }

            @Override
            public void onFailure(final Throwable cause) {
                ret.setException(cause instanceof ReadFailedException ? cause
                    : new ReadFailedException("NETCONF operation failed", cause));
            }
        }, MoreExecutors.directExecutor());
        return ret;
    }
}
