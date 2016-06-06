/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.restful.transaction.TransactionVarsWrapper;
import org.opendaylight.restconf.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Util class to post data to DS
 *
 */
public final class PostDataTransactionUtil {

    private static final Logger LOG = LoggerFactory.getLogger(PostDataTransactionUtil.class);

    private PostDataTransactionUtil() {
        throw new UnsupportedOperationException("Util class.");
    }

    /**
     * Check mount point and prepare variables for post data
     *
     * @param uriInfo
     *
     * @param payload
     *            - data
     * @param transactionNode
     *            - wrapper for transaction data
     * @param schemaContextRef
     *            - reference to actual {@link SchemaContext}
     * @return {@link CheckedFuture}
     */
    public static Response postData(final UriInfo uriInfo, final NormalizedNodeContext payload,
            final TransactionVarsWrapper transactionNode, final SchemaContextRef schemaContextRef) {
        final CheckedFuture<Void, TransactionCommitFailedException> future = submitData(
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData(),
                transactionNode, schemaContextRef.get());
        final URI location = PostDataTransactionUtil.resolveLocation(uriInfo, transactionNode, schemaContextRef);
        final ResponseFactory dataFactory = new ResponseFactory(
                ReadDataTransactionUtil.readData(RestconfDataServiceConstant.ReadData.CONFIG, transactionNode),
                location);
        FutureCallbackTx.addCallback(future, RestconfDataServiceConstant.PostData.POST_TX_TYPE, dataFactory);
        return dataFactory.build();
    }

    /**
     * Post data by type
     *
     * @param path
     *            - path
     * @param data
     *            - data
     * @param transactionNode
     *            - wrapper for data to transaction
     * @param schemaContext
     *            - schema context of data
     * @return {@link CheckedFuture}
     */
    private static CheckedFuture<Void, TransactionCommitFailedException> submitData(final YangInstanceIdentifier path,
            final NormalizedNode<?, ?> data, final TransactionVarsWrapper transactionNode,
            final SchemaContext schemaContext) {
        final DOMDataReadWriteTransaction transaction = transactionNode.getTransaction();
        final NormalizedNode<?, ?> node = ImmutableNodes.fromInstanceId(schemaContext, path);
        transaction.put(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.create(node.getIdentifier()), node);
        TransactionUtil.ensureParentsByMerge(path, schemaContext, transaction);

        if (data instanceof MapNode) {
            for (final MapEntryNode child : ((MapNode) data).getValue()) {
                putChild(child, transaction, path);
            }
        } else if (data instanceof AugmentationNode) {
            for (final DataContainerChild<? extends PathArgument, ?> child : ((AugmentationNode) data).getValue()) {
                putChild(child, transaction, path);
            }
        } else if (data instanceof ChoiceNode) {
            for (final DataContainerChild<? extends PathArgument, ?> child : ((ChoiceNode) data).getValue()) {
                putChild(child, transaction, path);
            }
        } else if (data instanceof LeafSetNode<?>) {
            for (final LeafSetEntryNode<?> child : ((LeafSetNode<?>) data).getValue()) {
                putChild(child, transaction, path);
            }
        } else if (data instanceof ContainerNode) {
            for (final DataContainerChild<? extends PathArgument, ?> child : ((ContainerNode) data).getValue()) {
                putChild(child, transaction, path);
            }
        }
        return transaction.submit();
    }

    /**
     * Prepare data for submit
     *
     * @param child
     *            - data
     * @param readWriteTx
     *            - transaction
     * @param path
     *            - path to data
     */
    private static void putChild(final NormalizedNode<?, ?> child, final DOMDataReadWriteTransaction readWriteTx,
            final YangInstanceIdentifier path) {
        final YangInstanceIdentifier childPath = path.node(child.getIdentifier());
        checkItemDesNotExits(childPath, readWriteTx);
        readWriteTx.put(LogicalDatastoreType.CONFIGURATION, childPath, child);
    }

    /**
     * Check if data posted to create doesn't exits.
     *
     * @param path
     *            - path to data
     * @param readWriteTx
     *            - read write transaction
     */
    private static void checkItemDesNotExits(final YangInstanceIdentifier path,
            final DOMDataReadWriteTransaction readWriteTx) {
        final ListenableFuture<Boolean> existData = readWriteTx.exists(LogicalDatastoreType.CONFIGURATION, path);
        try {
            if (existData.get()) {
                readWriteTx.cancel();
                throw new RestconfDocumentedException("Data already exists for path: " + path, ErrorType.PROTOCOL,
                        ErrorTag.DATA_EXISTS);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("It wasn't possible to get data loaded from datastore at path {}", path, e);
        }
    }

    /**
     * Get location from {@link YangInstanceIdentifier} and {@link UriInfo}
     *
     * @param uriInfo
     *            - uri info
     * @param transactionNode
     *            - wrapper for data of transaction
     * @param schemaContextRef
     *            -reference to {@link SchemaContext}
     * @return {@link URI}
     */
    private static URI resolveLocation(final UriInfo uriInfo, final TransactionVarsWrapper transactionNode,
            final SchemaContextRef schemaContextRef) {
        if (uriInfo == null) {
            return null;
        }

        final UriBuilder uriBuilder = uriInfo.getBaseUriBuilder();
        uriBuilder.path("data");
        uriBuilder.path(ParserIdentifier.stringFromYangInstanceIdentifier(transactionNode.getInstanceIdentifier().getInstanceIdentifier(),
                schemaContextRef.get()));

        return uriBuilder.build();
    }
}

