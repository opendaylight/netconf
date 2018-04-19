/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.handlers;

import com.google.common.base.Preconditions;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.IetfYangLibrary;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.MonitoringModule;
import org.opendaylight.restconf.nb.rfc8040.utils.mapping.RestconfMappingNodeUtil;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.tree.ConflictingModificationAppliedException;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link SchemaContextHandler}.
 *
 */
@SuppressWarnings("checkstyle:FinalClass")
public class SchemaContextHandler implements SchemaContextListenerHandler, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaContextHandler.class);

    private static final SchemaContextHandler INSTANCE = new SchemaContextHandler();

    private final AtomicInteger moduleSetId = new AtomicInteger(0);

    private TransactionChainHandler transactionChainHandler;
    private DOMSchemaService domSchemaService;
    private ListenerRegistration<SchemaContextListener> listenerRegistration;

    private volatile SchemaContext schemaContext;

    /**
     * Constructor.
     *
     * @param transactionChainHandler Transaction chain handler
     */
    private SchemaContextHandler(final TransactionChainHandler transactionChainHandler,
            final DOMSchemaService domSchemaService) {
        this.transactionChainHandler = transactionChainHandler;
        this.domSchemaService = domSchemaService;
    }

    @Deprecated
    private SchemaContextHandler() {
    }

    @Deprecated
    public static SchemaContextHandler instance() {
        return INSTANCE;
    }

    public static SchemaContextHandler newInstance(TransactionChainHandler transactionChainHandler,
            DOMSchemaService domSchemaService) {
        INSTANCE.transactionChainHandler = transactionChainHandler;
        INSTANCE.domSchemaService = domSchemaService;
        return INSTANCE;
    }

    public void init() {
        listenerRegistration = domSchemaService.registerSchemaContextListener(this);
    }

    @Override
    public void close() {
        if (listenerRegistration != null) {
            listenerRegistration.close();
        }
    }

    @Override
    @SuppressWarnings("checkstyle:hiddenField")
    public void onGlobalContextUpdated(final SchemaContext context) {
        Preconditions.checkNotNull(context);
        schemaContext = context;

        final Module ietfYangLibraryModule =
                context.findModule(IetfYangLibrary.MODULE_QNAME).orElse(null);
        if (ietfYangLibraryModule != null) {
            NormalizedNode<NodeIdentifier, Collection<DataContainerChild<? extends PathArgument, ?>>> normNode =
                    RestconfMappingNodeUtil.mapModulesByIetfYangLibraryYang(context.getModules(), ietfYangLibraryModule,
                            context, String.valueOf(this.moduleSetId.incrementAndGet()));
            putData(normNode);
        }

        final Module monitoringModule =
                schemaContext.findModule(MonitoringModule.MODULE_QNAME).orElse(null);
        if (monitoringModule != null) {
            NormalizedNode<NodeIdentifier, Collection<DataContainerChild<? extends PathArgument, ?>>> normNode =
                    RestconfMappingNodeUtil.mapCapabilites(monitoringModule);
            putData(normNode);
        }
    }

    @Override
    public SchemaContext get() {
        return schemaContext;
    }

    private void putData(
            final NormalizedNode<NodeIdentifier, Collection<DataContainerChild<? extends PathArgument, ?>>> normNode) {
        final DOMDataWriteTransaction wTx = this.transactionChainHandler.get().newWriteOnlyTransaction();
        wTx.put(LogicalDatastoreType.OPERATIONAL,
                YangInstanceIdentifier.create(NodeIdentifier.create(normNode.getNodeType())), normNode);
        try {
            wTx.submit().checkedGet();
        } catch (final TransactionCommitFailedException e) {
            if (!(e.getCause() instanceof ConflictingModificationAppliedException)) {
                throw new RestconfDocumentedException("Problem occurred while putting data to DS.", e);
            }

            /*
              Ignore error when another cluster node is already putting the same data to DS.
              We expect that cluster is homogeneous and that node was going to write the same data
              (that means no retry is needed). Transaction chain reset must be invoked to be able
              to continue writing data with another transaction after failed transaction.
              This is workaround for bug:
              https://bugs.opendaylight.org/show_bug.cgi?id=7728
            */
            LOG.warn("Ignoring that another cluster node is already putting the same data to DS.", e);
            this.transactionChainHandler.reset();
        }
    }
}
