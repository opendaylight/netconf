/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.handlers;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.aries.blueprint.annotation.service.Reference;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.IetfYangLibrary;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.MonitoringModule;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.CreateStreamUtil;
import org.opendaylight.restconf.nb.rfc8040.utils.mapping.RestconfMappingNodeUtil;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.tree.ConflictingModificationAppliedException;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link SchemaContextHandler}.
 */
@Singleton
@SuppressWarnings("checkstyle:FinalClass")
public class SchemaContextHandler implements SchemaContextListenerHandler, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaContextHandler.class);

    private final AtomicInteger moduleSetId = new AtomicInteger(0);

    private final TransactionChainHandler transactionChainHandler;
    private final DOMSchemaService domSchemaService;
    private ListenerRegistration<SchemaContextListener> listenerRegistration;

    private volatile SchemaContext schemaContext;

    /**
     * Constructor.
     *
     * @param transactionChainHandler Transaction chain handler
     */
    @Inject
    public SchemaContextHandler(final TransactionChainHandler transactionChainHandler,
            final @Reference DOMSchemaService domSchemaService) {
        this.transactionChainHandler = transactionChainHandler;
        this.domSchemaService = domSchemaService;
    }

    @Deprecated
    public static SchemaContextHandler newInstance(final TransactionChainHandler transactionChainHandler,
            final DOMSchemaService domSchemaService) {
        return new SchemaContextHandler(transactionChainHandler, domSchemaService);
    }

    @PostConstruct
    public void init() {
        listenerRegistration = domSchemaService.registerSchemaContextListener(this);
    }

    @Override
    @PreDestroy
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
        final DOMDataTreeReadWriteTransaction rwTransaction = transactionChainHandler.get().newReadWriteTransaction();

        context.findModule(IetfYangLibrary.MODULE_QNAME).ifPresent(module -> {
            final NormalizedNode<NodeIdentifier, Collection<DataContainerChild<? extends PathArgument, ?>>> normNode =
                    RestconfMappingNodeUtil.mapModulesByIetfYangLibraryYang(context.getModules(), module,
                            context, String.valueOf(this.moduleSetId.incrementAndGet()));
            rwTransaction.put(LogicalDatastoreType.OPERATIONAL, YangInstanceIdentifier.create(
                    NodeIdentifier.create(normNode.getNodeType())), normNode);
        });

        schemaContext.findModule(MonitoringModule.MODULE_QNAME).ifPresent(module -> {
            final NormalizedNode<NodeIdentifier, Collection<DataContainerChild<? extends PathArgument, ?>>> normNode =
                    RestconfMappingNodeUtil.mapCapabilites(module);
            rwTransaction.put(LogicalDatastoreType.OPERATIONAL, YangInstanceIdentifier.create(
                    NodeIdentifier.create(normNode.getNodeType())), normNode);
            CreateStreamUtil.createYangNotifiStreams(schemaContext, rwTransaction);
        });

        commitTransaction(rwTransaction);
    }

    @Override
    public SchemaContext get() {
        return schemaContext;
    }

    private void commitTransaction(final DOMDataTreeWriteTransaction transaction) {
        try {
            transaction.commit().get();
        } catch (InterruptedException e) {
            throw new RestconfDocumentedException("Problem occurred while putting data to DS.", e);
        } catch (ExecutionException e) {
            final TransactionCommitFailedException failure = Throwables.getCauseAs(e,
                    TransactionCommitFailedException.class);
            if (failure.getCause() instanceof ConflictingModificationAppliedException) {
                /*
                 * Ignore error when another cluster node is already putting the same data to DS.
                 * We expect that cluster is homogeneous and that node was going to write the same data
                 * (that means no retry is needed). Transaction chain reset must be invoked to be able
                 * to continue writing data with another transaction after failed transaction.
                 * This is workaround for bug https://bugs.opendaylight.org/show_bug.cgi?id=7728
                 */
                LOG.warn("Ignoring that another cluster node is already putting the same data to DS.", e);
                this.transactionChainHandler.reset();
            } else {
                throw new RestconfDocumentedException("Problem occurred while putting data to DS.", failure);
            }
        }
    }
}