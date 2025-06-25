/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
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
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.api.query.WithDefaultsParam;
import org.opendaylight.restconf.mdsal.spi.data.AsyncGetDataProcessor;
import org.opendaylight.restconf.mdsal.spi.data.RestconfStrategy;
import org.opendaylight.restconf.mdsal.spi.data.RestconfTransaction;
import org.opendaylight.restconf.server.api.DataGetParams;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.NormalizedFormattableBody;
import org.opendaylight.restconf.server.spi.NormalizedNodeWriterFactory;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * Implementation of RESTCONF operations on top of a raw NETCONF backend.
 *
 * @see NetconfDataTreeService
 */
// FIXME: remove this class: we really want to provide ServerDataOperations instead
@VisibleForTesting
public final class NetconfRestconfStrategy extends RestconfStrategy {
    private final NetconfDataTreeService netconfService;

    public NetconfRestconfStrategy(final DatabindContext databind, final NetconfDataTreeService netconfService) {
        super(databind);
        this.netconfService = requireNonNull(netconfService);
    }

    @Override
    protected RestconfTransaction prepareWriteExecution() {
        return new NetconfRestconfTransaction(databind, netconfService);
    }

    @Override
    public void deleteData(final ServerRequest<Empty> request, final Data path) {
        final var tx = prepareWriteExecution();
        try {
            tx.delete(path.instance());
        } catch (RequestException e) {
            tx.cancel();
            request.completeWith(e);
            return;
        }

        Futures.addCallback(tx.commit(), new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                request.completeWith(Empty.value());
            }

            @Override
            public void onFailure(final Throwable cause) {
                request.completeWith(decodeException(cause, "DELETE", path));
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    public void getData(final ServerRequest<DataGetResult> request, final Data path, final DataGetParams params) {
        final List<YangInstanceIdentifier> fieldPaths;
        try {
            fieldPaths = FieldsParamParser.fieldsParamsToPaths(path.inference().modelContext(), path.schema(),
                params.fields());
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }
        final var netconfGetRequest = request.<Optional<NormalizedNode>>transform(result -> {
            if (result.isEmpty()) {
                throw new IllegalStateException("Request transformation could not be completed without data");
            }
            final var normalizedNode = result.orElseThrow();
            final var writerFactory = NormalizedNodeWriterFactory.of(params.depth());
            final var body = NormalizedFormattableBody.of(path, normalizedNode, writerFactory);
            return new DataGetResult(body);
        });

        if (fieldPaths.isEmpty()) {
            readData(netconfGetRequest, params.content(), path, params.withDefaults());
        } else {
            readData(netconfGetRequest, params.content(), path, params.withDefaults(), fieldPaths);
        }
    }

    @Override
    protected ListenableFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store,
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
     * @param getRequest    {@link ServerRequest} with {@link Optional} {@link NormalizedNode} result
     * @param content       type of data to read (config, state, all)
     * @param path          the parent path to read
     * @param withDefa      value of with-defaults parameter
     * @param fields        paths to selected subtrees which should be read, relative to the parent path
     */
    public void readData(final @NonNull ServerRequest<Optional<NormalizedNode>> getRequest,
            final @NonNull ContentParam content, final @NonNull Data path,
            final @Nullable WithDefaultsParam withDefa, final @NonNull List<YangInstanceIdentifier> fields) {
        final var instance = path.instance();
        final var processor = new AsyncGetDataProcessor(path, withDefa);
        final var getFuture = switch (content) {
            case ALL -> {
                // PREPARE STATE DATA NODE
                final var stateDataNode = read(LogicalDatastoreType.OPERATIONAL, instance, fields);
                // PREPARE CONFIG DATA NODE
                final var configDataNode = read(LogicalDatastoreType.CONFIGURATION, instance, fields);
                yield processor.all(stateDataNode, configDataNode);
            }
            case CONFIG -> processor.config(read(LogicalDatastoreType.CONFIGURATION, instance, fields));
            case NONCONFIG -> processor.nonConfig(read(LogicalDatastoreType.OPERATIONAL, instance, fields));
        };

        Futures.addCallback(getFuture, new FutureCallback<>() {
            @Override
            public void onSuccess(Optional<NormalizedNode> result) {
                if (result.isPresent()) {
                    getRequest.completeWith(result);
                } else {
                    getRequest.completeWith(new RequestException(ErrorType.PROTOCOL, ErrorTag.DATA_MISSING,
                        "Request could not be completed because the relevant data model content does not exist"));
                }
            }

            @Override
            public void onFailure(Throwable cause) {
                getRequest.completeWith(decodeException(cause, "GET", path));
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    protected ListenableFuture<Boolean> exists(final YangInstanceIdentifier path) {
        return Futures.transform(remapException(netconfService.getConfig(path)),
            optionalNode -> optionalNode != null && optionalNode.isPresent(),
            MoreExecutors.directExecutor());
    }

    private static <T> ListenableFuture<T> remapException(final ListenableFuture<T> input) {
        final var ret = SettableFuture.<T>create();
        Futures.addCallback(input, new FutureCallback<>() {
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
