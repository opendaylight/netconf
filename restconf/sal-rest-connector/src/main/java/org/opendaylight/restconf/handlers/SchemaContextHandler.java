/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.handlers;

import com.google.common.base.Preconditions;
import java.util.Collection;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.restconf.Draft18.IetfYangLibrary;
import org.opendaylight.restconf.utils.mapping.RestconfMappingNodeUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Implementation of {@link SchemaContextHandler}
 *
 */
public class SchemaContextHandler implements SchemaContextListenerHandler {

    private SchemaContext context;

    private int moduleSetId;
    private final TransactionChainHandler transactionChainHandler;

    /**
     * Set module-set-id on initial value - 0
     *
     * @param transactionChainHandler
     */
    public SchemaContextHandler(final TransactionChainHandler transactionChainHandler) {
        this.transactionChainHandler = transactionChainHandler;
        this.moduleSetId = 0;
    }

    @Override
    public void onGlobalContextUpdated(final SchemaContext context) {
        Preconditions.checkNotNull(context);
        this.context = null;
        this.context = context;
        this.moduleSetId++;
        final Module ietfYangLibraryModule =
                context.findModuleByNamespaceAndRevision(IetfYangLibrary.URI_MODULE, IetfYangLibrary.DATE);
        final NormalizedNode<NodeIdentifier, Collection<DataContainerChild<? extends PathArgument, ?>>> normNode =
                RestconfMappingNodeUtil.mapModulesByIetfYangLibraryYang(context.getModules(), ietfYangLibraryModule,
                        context, String.valueOf(this.moduleSetId));
        final DOMDataWriteTransaction wTx = this.transactionChainHandler.get().newWriteOnlyTransaction();
        wTx.put(LogicalDatastoreType.OPERATIONAL,
                YangInstanceIdentifier.create(NodeIdentifier.create(normNode.getNodeType())), normNode);
        try {
            wTx.submit().checkedGet();
        } catch (final TransactionCommitFailedException e) {
            throw new RestconfDocumentedException("Problem occured while putting data to DS.", e);
        }
    }

    @Override
    public SchemaContext get() {
        return this.context;
    }
}
