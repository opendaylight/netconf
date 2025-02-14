/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.mdsal.spi.data;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.netconf.databind.ErrorPath;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.server.api.DataGetParams;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.NormalizedNodeWriter;
import org.opendaylight.restconf.server.spi.NormalizedNodeWriterFactory;
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
    private final @NonNull DOMDataBroker dataBroker;

    public MdsalRestconfStrategy(final DatabindContext databind, final DOMDataBroker dataBroker) {
        super(databind);
        this.dataBroker = requireNonNull(dataBroker);
    }

    @Override
    protected RestconfTransaction prepareWriteExecution() {
        return new MdsalRestconfTransaction(databind, dataBroker);
    }

    @Override
    public void deleteData(final ServerRequest<Empty> request, final Data path) {
        final var tx = dataBroker.newReadWriteTransaction();
        tx.exists(LogicalDatastoreType.CONFIGURATION, path.instance()).addCallback(new FutureCallback<>() {
            @Override
            public void onSuccess(final Boolean result) {
                if (!result) {
                    cancelTx(new RequestException(ErrorType.PROTOCOL, ErrorTag.DATA_MISSING, "Data does not exist",
                        new ErrorPath(path)));
                    return;
                }

                tx.delete(LogicalDatastoreType.CONFIGURATION, path.instance());
                tx.commit().addCallback(new FutureCallback<CommitInfo>() {
                    @Override
                    public void onSuccess(final CommitInfo result) {
                        request.completeWith(Empty.value());
                    }

                    @Override
                    public void onFailure(final Throwable cause) {
                        request.completeWith(new RequestException("Transaction to delete failed", new ErrorPath(path),
                            cause));
                    }
                }, MoreExecutors.directExecutor());
            }

            @Override
            public void onFailure(final Throwable cause) {
                cancelTx(new RequestException("Failed to access " + path, cause));
            }

            private void cancelTx(final RequestException ex) {
                tx.cancel();
                request.completeWith(ex);
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    public void getData(final ServerRequest<DataGetResult> request, final Data path, final DataGetParams params) {
        final var depth = params.depth();
        final var fields = params.fields();

        final NormalizedNodeWriterFactory writerFactory;
        if (fields != null) {
            final List<Set<QName>> translated;
            try {
                translated = NormalizedNodeWriter.translateFieldsParam(path.inference().modelContext(), path.schema(),
                    fields);
            } catch (RequestException e) {
                request.completeWith(e);
                return;
            }
            writerFactory = new MdsalNormalizedNodeWriterFactory(translated, depth);
        } else {
            writerFactory = NormalizedNodeWriterFactory.of(depth);
        }

        final NormalizedNode data;
        try {
            data = readData(params.content(), path.instance(), params.withDefaults());
        } catch (RequestException e) {
            request.completeWith(e);
            return;
        }

        completeDataGET(request, data, path, writerFactory, null);
    }

    @Override
    protected ListenableFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        try (var tx = dataBroker.newReadOnlyTransaction()) {
            return tx.read(store, path);
        }
    }

    @Override
    protected ListenableFuture<Boolean> exists(final YangInstanceIdentifier path) {
        try (var tx = dataBroker.newReadOnlyTransaction()) {
            return tx.exists(LogicalDatastoreType.CONFIGURATION, path);
        }
    }
}
