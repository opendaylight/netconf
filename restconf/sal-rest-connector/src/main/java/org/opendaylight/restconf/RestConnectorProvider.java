/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf;

import com.google.common.base.Preconditions;
import java.util.Collection;
import java.util.Collections;
import org.opendaylight.controller.md.sal.common.api.data.AsyncTransaction;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChain;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.controller.sal.core.api.Broker.ProviderSession;
import org.opendaylight.controller.sal.core.api.Provider;
import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.netconf.sal.rest.api.RestConnector;
import org.opendaylight.restconf.common.handlers.api.DOMDataBrokerHandler;
import org.opendaylight.restconf.common.handlers.api.DOMMountPointServiceHandler;
import org.opendaylight.restconf.common.handlers.api.SchemaContextHandler;
import org.opendaylight.restconf.common.handlers.api.TransactionChainHandler;
import org.opendaylight.restconf.common.handlers.impl.DOMDataBrokerHandlerImpl;
import org.opendaylight.restconf.common.handlers.impl.DOMMountPointServiceHandlerImpl;
import org.opendaylight.restconf.common.handlers.impl.SchemaContextHandlerImpl;
import org.opendaylight.restconf.common.handlers.impl.TransactionChainHandlerImpl;
import org.opendaylight.restconf.rest.services.impl.Draft11ServicesWrapperImpl;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provider for restconf draft11.
 *
 */
public class RestConnectorProvider implements Provider, RestConnector, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RestConnectorProvider.class);

    private final TransactionChainListener transactionListener = new TransactionChainListener() {
        @Override
        public void onTransactionChainFailed(final TransactionChain<?, ?> chain,
                final AsyncTransaction<?, ?> transaction, final Throwable cause) {
            LOG.error("TransactionChain({}) {} FAILED!", chain, transaction.getIdentifier(), cause);
            chain.close();
            resetTransactionChainForAdapaters(chain);
            throw new IllegalStateException("TransactionChain(" + chain + ") not committed correctly", cause);
        }

        @Override
        public void onTransactionChainSuccessful(final TransactionChain<?, ?> chain) {
            LOG.trace("TransactionChain({}) {} SUCCESSFUL", chain);
        }
    };

    private ListenerRegistration<SchemaContextListener> listenerRegistration;
    private DOMDataBroker dataBroker;
    private DOMTransactionChain transactionChain;
    private TransactionChainHandler transactionChainHandler;

    @Override
    public void onSessionInitiated(final ProviderSession session) {
        final SchemaService schemaService = Preconditions.checkNotNull(session.getService(SchemaService.class));
        final DOMMountPointServiceHandler domMountPointServiceHandler = new DOMMountPointServiceHandlerImpl();
        final SchemaContextHandler schemaCtxHandler = new SchemaContextHandlerImpl();
        domMountPointServiceHandler.setDOMMountPointService(session.getService(DOMMountPointService.class));
        final Draft11ServicesWrapperImpl wrapperServices = Draft11ServicesWrapperImpl.getInstance();
        this.listenerRegistration = schemaService.registerSchemaContextListener(schemaCtxHandler);

        this.transactionChainHandler = new TransactionChainHandlerImpl();
        this.dataBroker = session.getService(DOMDataBroker.class);
        this.transactionChain = this.dataBroker.createTransactionChain(this.transactionListener);
        this.transactionChainHandler.setTransactionChain(this.transactionChain);

        final DOMDataBrokerHandler brokerHandler = new DOMDataBrokerHandlerImpl();
        brokerHandler.setDOMDataBroker(this.dataBroker);

        wrapperServices.setHandlers(schemaCtxHandler, domMountPointServiceHandler);
    }

    /**
     * Resetting {@link TransactionChain} because of failed.
     *
     * @param chain
     *            - old {@link TransactionChain}
     */
    private void resetTransactionChainForAdapaters(final TransactionChain<?, ?> chain) {
        this.transactionChain = Preconditions.checkNotNull(this.dataBroker)
                .createTransactionChain(this.transactionListener);

        this.transactionChainHandler.setTransactionChain(this.transactionChain);

        LOG.trace("Resetting TransactionChain({}) to {}", chain, this.transactionChain);
    }

    @Override
    public Collection<ProviderFunctionality> getProviderFunctionality() {
        return Collections.emptySet();
    }

    @Override
    public void close() throws Exception {
        if (this.listenerRegistration != null) {
            this.listenerRegistration.close();
        }
        if (this.transactionChain != null) {
            this.transactionChain.close();
        }
    }
}
