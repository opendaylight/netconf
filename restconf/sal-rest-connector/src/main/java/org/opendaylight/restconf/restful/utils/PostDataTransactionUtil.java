/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils;

import com.google.common.util.concurrent.CheckedFuture;
import java.net.URI;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.restful.transaction.TransactionVarsWrapper;
import org.opendaylight.restconf.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
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
        final ResponseFactory dataFactory = new ResponseFactory(null, location);
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
        final DOMTransactionChain transactionChain = transactionNode.getTransactionChain();
        final DOMDataReadWriteTransaction transaction = transactionChain.newReadWriteTransaction();

        if (data instanceof MapNode) {
            boolean merge = false;
            for (final MapEntryNode child : ((MapNode) data).getValue()) {
                final YangInstanceIdentifier childPath = path.node(child.getIdentifier());
                TransactionUtil.checkItemDoesNotExists(
                        transactionChain, transaction, LogicalDatastoreType.CONFIGURATION, childPath,
                        RestconfDataServiceConstant.PostData.POST_TX_TYPE);
                if (!merge) {
                    merge = true;
                    TransactionUtil.ensureParentsByMerge(path, schemaContext, transaction);
                    final NormalizedNode<?, ?> emptySubTree = ImmutableNodes.fromInstanceId(schemaContext, path);
                    transaction.merge(LogicalDatastoreType.CONFIGURATION,
                            YangInstanceIdentifier.create(emptySubTree.getIdentifier()), emptySubTree);
                }
                transaction.put(LogicalDatastoreType.CONFIGURATION, childPath, child);
            }
        } else {
            TransactionUtil.checkItemDoesNotExists(
                    transactionChain, transaction, LogicalDatastoreType.CONFIGURATION, path,
                    RestconfDataServiceConstant.PostData.POST_TX_TYPE);

            TransactionUtil.ensureParentsByMerge(path, schemaContext, transaction);
            transaction.put(LogicalDatastoreType.CONFIGURATION, path, data);
        }

        return transaction.submit();
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
