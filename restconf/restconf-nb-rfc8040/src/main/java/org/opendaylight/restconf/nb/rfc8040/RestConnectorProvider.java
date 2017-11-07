/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040;

import com.google.common.base.Preconditions;
import org.opendaylight.controller.md.sal.common.api.data.AsyncTransaction;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChain;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMDataBrokerHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.NotificationServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.RpcServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants;
import org.opendaylight.restconf.nb.rfc8040.services.wrapper.ServiceWrapper;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provider for restconf draft18.
 *
 */
public class RestConnectorProvider<T extends ServiceWrapper> implements RestconfConnector, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RestConnectorProvider.class);

    public static final TransactionChainListener TRANSACTION_CHAIN_LISTENER = new TransactionChainListener() {
        @Override
        public void onTransactionChainFailed(final TransactionChain<?, ?> chain,
                final AsyncTransaction<?, ?> transaction, final Throwable cause) {
            LOG.warn("TransactionChain({}) {} FAILED!", chain, transaction.getIdentifier(), cause);
            resetTransactionChainForAdapaters(chain);
            throw new RestconfDocumentedException("TransactionChain(" + chain + ") not committed correctly", cause);
        }

        @Override
        public void onTransactionChainSuccessful(final TransactionChain<?, ?> chain) {
            LOG.trace("TransactionChain({}) {} SUCCESSFUL", chain);
        }
    };

    private static TransactionChainHandler transactionChainHandler;
    private static DOMDataBroker dataBroker;
    private static DOMMountPointServiceHandler mountPointServiceHandler;

    private final SchemaService schemaService;
    private final DOMRpcService rpcService;
    private final DOMNotificationService notificationService;
    private final DOMMountPointService mountPointService;
    private final T wrapperServices;

    private ListenerRegistration<SchemaContextListener> listenerRegistration;
    private SchemaContextHandler schemaCtxHandler;

    private final String schema;

    public RestConnectorProvider(final DOMDataBroker domDataBroker, final SchemaService schemaService,
            final DOMRpcService rpcService, final DOMNotificationService notificationService,
            final DOMMountPointService mountPointService, final T wrapperServices) {
        this(domDataBroker, schemaService, rpcService, notificationService, mountPointService, wrapperServices,
                RestconfStreamsConstants.SCHEMA_SUBSCIBRE_URI);

    }

    public RestConnectorProvider(final DOMDataBroker domDataBroker, final SchemaService schemaService,
            final DOMRpcService rpcService, final DOMNotificationService notificationService,
            final DOMMountPointService mountPointService, final T wrapperServices, final String schema) {
        this.wrapperServices = wrapperServices;
        this.schemaService = Preconditions.checkNotNull(schemaService);
        this.rpcService = Preconditions.checkNotNull(rpcService);
        this.notificationService = Preconditions.checkNotNull(notificationService);
        this.mountPointService = Preconditions.checkNotNull(mountPointService);
        this.schema = schema;
        RestConnectorProvider.dataBroker = Preconditions.checkNotNull(domDataBroker);
    }

    public void start() {
        mountPointServiceHandler = new DOMMountPointServiceHandler(mountPointService);

        final DOMDataBrokerHandler brokerHandler = new DOMDataBrokerHandler(dataBroker);

        RestConnectorProvider.transactionChainHandler = new TransactionChainHandler(dataBroker
                .createTransactionChain(RestConnectorProvider.TRANSACTION_CHAIN_LISTENER));

        this.schemaCtxHandler = new SchemaContextHandler(transactionChainHandler);
        this.listenerRegistration = schemaService.registerSchemaContextListener(this.schemaCtxHandler);

        final RpcServiceHandler rpcServiceHandler = new RpcServiceHandler(rpcService);

        final NotificationServiceHandler notificationServiceHandler =
                new NotificationServiceHandler(notificationService);

        wrapperServices.setHandlers(this.schemaCtxHandler, RestConnectorProvider.mountPointServiceHandler,
                RestConnectorProvider.transactionChainHandler, brokerHandler, rpcServiceHandler,
                notificationServiceHandler, schema);
    }

    public DOMMountPointServiceHandler getMountPointServiceHandler() {
        return mountPointServiceHandler;
    }

    /**
     * After {@link TransactionChain} failed, this updates {@link TransactionChainHandler} with new transaction chain.
     *
     * @param chain
     *             old {@link TransactionChain}
     */
    public static void resetTransactionChainForAdapaters(final TransactionChain<?, ?> chain) {
        LOG.trace("Resetting TransactionChain({})", chain);
        chain.close();
        RestConnectorProvider.transactionChainHandler.update(
                Preconditions.checkNotNull(dataBroker).createTransactionChain(
                        RestConnectorProvider.TRANSACTION_CHAIN_LISTENER)
        );
    }

    /**
     * Get current {@link DOMMountPointService} from {@link DOMMountPointServiceHandler}.
     * @return {@link DOMMountPointService}
     */
    public static DOMMountPointService getMountPointService() {
        return mountPointServiceHandler.get();
    }

    @Override
    public void close() throws Exception {
        // close registration
        if (this.listenerRegistration != null) {
            this.listenerRegistration.close();
        }

        // close transaction chain
        if (transactionChainHandler != null && transactionChainHandler.get() != null) {
            transactionChainHandler.get().close();
        }

        transactionChainHandler = null;
        mountPointServiceHandler = null;
        dataBroker = null;
    }
}
