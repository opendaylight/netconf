/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.common.handlers.api.TransactionChainHandler;
import org.opendaylight.restconf.common.references.SchemaContextRef;
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

public class PostDataTransactionUtil {

    private static final Logger LOG = LoggerFactory.getLogger(PostDataTransactionUtil.class);

    public static CheckedFuture<Void, TransactionCommitFailedException> postData(final NormalizedNodeContext payload,
            final UriInfo uriInfo, final TransactionChainHandler transactionChainHandler, final SchemaContextRef schemaContextRef) {
        final DOMMountPoint mountPoint = payload.getInstanceIdentifierContext().getMountPoint();
        final YangInstanceIdentifier path = payload.getInstanceIdentifierContext()
                .getInstanceIdentifier();
        final DOMDataReadWriteTransaction readWriteTx = transactionChainHandler.getTransactionChain()
                .newReadWriteTransaction();
        if (mountPoint == null) {
            return submitData(path, schemaContextRef.get(), payload.getData(), readWriteTx);
        } else {
            final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);

            if (domDataBrokerService.isPresent()) {
                return submitData(path, mountPoint.getSchemaContext(), payload.getData(), readWriteTx);
            }
            final String errMsg = "DOM data broker service isn't available for mount point " + path;
            throw new RestconfDocumentedException(errMsg);
        }
    }

    private static CheckedFuture<Void, TransactionCommitFailedException> submitData(final YangInstanceIdentifier path,
            final SchemaContext schemaContext, final NormalizedNode<?, ?> data,
            final DOMDataReadWriteTransaction readWriteTx) {

        final NormalizedNode<?, ?> node = ImmutableNodes.fromInstanceId(schemaContext, path);
        readWriteTx.merge(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.create(node.getIdentifier()),
                node);
        TransactionUtil.ensureParentsByMerge(path, schemaContext, readWriteTx);

        if (data instanceof MapNode) {
            for (final MapEntryNode child : ((MapNode) data).getValue()) {
                putChild(child, readWriteTx, path);
            }
        } else if (data instanceof AugmentationNode) {
            for (final DataContainerChild<? extends PathArgument, ?> child : ((AugmentationNode) node).getValue()) {
                putChild(child, readWriteTx, path);
            }
        } else if (data instanceof ChoiceNode) {
            for (final DataContainerChild<? extends PathArgument, ?> child : ((ChoiceNode) data).getValue()) {
                putChild(child, readWriteTx, path);
            }
        } else if (data instanceof LeafSetNode<?>) {
            for (final LeafSetEntryNode<?> child : ((LeafSetNode<?>) data).getValue()) {
                putChild(child, readWriteTx, path);
            }
        } else if (data instanceof ContainerNode) {
            for (final DataContainerChild<? extends PathArgument, ?> child : ((ContainerNode) data).getValue()) {
                putChild(child, readWriteTx, path);
            }
        }
        return readWriteTx.submit();
    }

    private static void putChild(final NormalizedNode<?, ?> child, final DOMDataReadWriteTransaction readWriteTx,
            final YangInstanceIdentifier path) {
        final YangInstanceIdentifier childPath = path.node(child.getIdentifier());
        checkItemDesNotExits(childPath, readWriteTx, LogicalDatastoreType.CONFIGURATION);
        readWriteTx.put(LogicalDatastoreType.CONFIGURATION, childPath, child);
    }

    private static void checkItemDesNotExits(final YangInstanceIdentifier path,
            final DOMDataReadWriteTransaction readWriteTx, final LogicalDatastoreType store) {
        final ListenableFuture<Boolean> existData = readWriteTx.exists(store, path);
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

}

