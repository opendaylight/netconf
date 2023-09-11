/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.List;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.api.query.PointParam;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.common.errors.SettableRestconfFuture;
import org.opendaylight.restconf.nb.rfc8040.WriteDataParams;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.PostDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.TransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.YangInstanceIdentifierDeserializer;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodeContainer;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Baseline execution strategy for various RESTCONF operations.
 *
 * @see NetconfRestconfStrategy
 * @see MdsalRestconfStrategy
 */
// FIXME: it seems the first three operations deal with lifecycle of a transaction, while others invoke various
//        operations. This should be handled through proper allocation indirection.
public abstract class RestconfStrategy {
    /**
     * Result of a {@code PUT} request as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.5">RFC8040 section 4.5</a>. The definition makes it
     * clear that the logical operation is {@code create-or-replace}.
     */
    public enum CreateOrReplaceResult {
        /**
         * A new resource has been created.
         */
        CREATED,
        /*
         * An existing resources has been replaced.
         */
        REPLACED;
    }

    RestconfStrategy() {
        // Hidden on purpose
    }

    /**
     * Look up the appropriate strategy for a particular mount point.
     *
     * @param mountPoint Target mount point
     * @return A strategy, or null if the mount point does not expose a supported interface
     * @throws NullPointerException if {@code mountPoint} is null
     */
    public static Optional<RestconfStrategy> forMountPoint(final DOMMountPoint mountPoint) {
        final Optional<RestconfStrategy> netconf = mountPoint.getService(NetconfDataTreeService.class)
            .map(NetconfRestconfStrategy::new);
        if (netconf.isPresent()) {
            return netconf;
        }

        return mountPoint.getService(DOMDataBroker.class).map(MdsalRestconfStrategy::new);
    }

    /**
     * Lock the entire datastore.
     *
     * @return A {@link RestconfTransaction}. This transaction needs to be either committed or canceled before doing
     *         anything else.
     */
    public abstract RestconfTransaction prepareWriteExecution();

    /**
     * Read data from the datastore.
     *
     * @param store the logical data store which should be modified
     * @param path the data object path
     * @return a ListenableFuture containing the result of the read
     */
    public abstract ListenableFuture<Optional<NormalizedNode>> read(LogicalDatastoreType store,
        YangInstanceIdentifier path);

    /**
     * Read data selected using fields from the datastore.
     *
     * @param store the logical data store which should be modified
     * @param path the parent data object path
     * @param fields paths to selected fields relative to parent path
     * @return a ListenableFuture containing the result of the read
     */
    public abstract ListenableFuture<Optional<NormalizedNode>> read(LogicalDatastoreType store,
            YangInstanceIdentifier path, List<YangInstanceIdentifier> fields);

    /**
     * Check if data already exists in the datastore.
     *
     * @param store the logical data store which should be modified
     * @param path the data object path
     * @return a FluentFuture containing the result of the check
     */
    public abstract ListenableFuture<Boolean> exists(LogicalDatastoreType store, YangInstanceIdentifier path);

    /**
     * Delete data from the configuration datastore. If the data does not exist, this operation will fail, as outlined
     * in <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.7">RFC8040 section 4.7</a>
     *
     * @param path Path to delete
     * @return A {@link RestconfFuture}
     * @throws NullPointerException if {@code path} is {@code null}
     */
    public final @NonNull RestconfFuture<Empty> delete(final YangInstanceIdentifier path) {
        final var ret = new SettableRestconfFuture<Empty>();
        delete(ret, requireNonNull(path));
        return ret;
    }

    protected abstract void delete(@NonNull SettableRestconfFuture<Empty> future, @NonNull YangInstanceIdentifier path);

    /**
     * Merge data into the configuration datastore, as outlined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1">RFC8040 section 4.6.1</a>.
     *
     * @param path Path to merge
     * @param data Data to merge
     * @param context Corresponding EffectiveModelContext
     * @return A {@link RestconfFuture}
     * @throws NullPointerException if any argument is {@code null}
     */
    public final @NonNull RestconfFuture<Empty> merge(final YangInstanceIdentifier path, final NormalizedNode data,
            final EffectiveModelContext context) {
        final var ret = new SettableRestconfFuture<Empty>();
        merge(ret, requireNonNull(path), requireNonNull(data), requireNonNull(context));
        return ret;
    }

    private void merge(final @NonNull SettableRestconfFuture<Empty> future,
            final @NonNull YangInstanceIdentifier path, final @NonNull NormalizedNode data,
            final @NonNull EffectiveModelContext context) {
        final var tx = prepareWriteExecution();
        // FIXME: this method should be further specialized to eliminate this call -- it is only needed for MD-SAL
        TransactionUtil.ensureParentsByMerge(path, context, tx);
        tx.merge(path, data);
        Futures.addCallback(tx.commit(), new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                future.set(Empty.value());
            }

            @Override
            public void onFailure(final Throwable cause) {
                future.setFailure(TransactionUtil.decodeException(cause, "MERGE", path));
            }
        }, MoreExecutors.directExecutor());
    }

    /**
     * Check mount point and prepare variables for put data to DS. Close {@link DOMTransactionChain} if any
     * inside of object {@link RestconfStrategy} provided as a parameter if any.
     *
     * @param path          path of data
     * @param data          data
     * @param schemaContext reference to {@link EffectiveModelContext}
     * @param params        {@link WriteDataParams}
     * @return A {@link CreateOrReplaceResult}
     */
    public @NonNull CreateOrReplaceResult putData(final YangInstanceIdentifier path, final NormalizedNode data,
            final EffectiveModelContext schemaContext, final WriteDataParams params) {
        final var exists = TransactionUtil.syncAccess(exists(LogicalDatastoreType.CONFIGURATION, path), path);
        TransactionUtil.syncCommit(submitData(path, schemaContext, data, params), "PUT", path);
        return exists ? CreateOrReplaceResult.REPLACED : CreateOrReplaceResult.CREATED;
    }

    /**
     * Put data to DS.
     *
     * @param path          path of data
     * @param schemaContext {@link SchemaContext}
     * @param data          data
     * @param params        {@link WriteDataParams}
     * @return A {@link ListenableFuture}
     */
    private ListenableFuture<? extends CommitInfo> submitData(final YangInstanceIdentifier path,
            final EffectiveModelContext schemaContext, final NormalizedNode data, final WriteDataParams params) {
        final var transaction = prepareWriteExecution();
        final var insert = params.insert();
        if (insert == null) {
            return makePut(path, schemaContext, transaction, data);
        }

        final var parentPath = path.coerceParent();
        PostDataTransactionUtil.checkListAndOrderedType(schemaContext, parentPath);

        return switch (insert) {
            case FIRST -> {
                final var readData = transaction.readList(parentPath);
                if (readData == null || readData.isEmpty()) {
                    yield makePut(path, schemaContext, transaction, data);
                }
                transaction.remove(parentPath);
                transaction.replace(path, data, schemaContext);
                transaction.replace(parentPath, readData, schemaContext);
                yield transaction.commit();
            }
            case LAST -> makePut(path, schemaContext, transaction, data);
            case BEFORE -> {
                final var readData = transaction.readList(parentPath);
                if (readData == null || readData.isEmpty()) {
                    yield makePut(path, schemaContext, transaction, data);
                }
                insertWithPointPut(transaction, path, data, schemaContext, params.getPoint(), readData, true);
                yield transaction.commit();
            }
            case AFTER -> {
                final var readData = transaction.readList(parentPath);
                if (readData == null || readData.isEmpty()) {
                    yield makePut(path, schemaContext, transaction, data);
                }
                insertWithPointPut(transaction, path, data, schemaContext, params.getPoint(), readData, false);
                yield transaction.commit();
            }
        };
    }

    private static void insertWithPointPut(final RestconfTransaction transaction, final YangInstanceIdentifier path,
            final NormalizedNode data, final EffectiveModelContext schemaContext, final PointParam point,
            final NormalizedNodeContainer<?> readList, final boolean before) {
        transaction.remove(path.getParent());
        final var pointArg = YangInstanceIdentifierDeserializer.create(schemaContext, point.value()).path
            .getLastPathArgument();
        int lastItemPosition = 0;
        for (var nodeChild : readList.body()) {
            if (nodeChild.name().equals(pointArg)) {
                break;
            }
            lastItemPosition++;
        }
        if (!before) {
            lastItemPosition++;
        }
        int lastInsertedPosition = 0;
        final var emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, path.getParent());
        transaction.merge(YangInstanceIdentifier.of(emptySubtree.name()), emptySubtree);
        for (var nodeChild : readList.body()) {
            if (lastInsertedPosition == lastItemPosition) {
                transaction.replace(path, data, schemaContext);
            }
            final var childPath = path.coerceParent().node(nodeChild.name());
            transaction.replace(childPath, nodeChild, schemaContext);
            lastInsertedPosition++;
        }
    }

    private static ListenableFuture<? extends CommitInfo> makePut(final YangInstanceIdentifier path,
            final EffectiveModelContext schemaContext, final RestconfTransaction transaction,
            final NormalizedNode data) {
        transaction.replace(path, data, schemaContext);
        return transaction.commit();
    }
}
