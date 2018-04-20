/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040;

import com.google.common.base.Preconditions;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMDataBrokerHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.NotificationServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.RpcServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.services.wrapper.ServiceWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provider for restconf draft18.
 *
 */
public class RestConnectorProvider<T extends ServiceWrapper> implements RestconfConnector, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RestConnectorProvider.class);

    private final DOMRpcService rpcService;
    private final DOMNotificationService notificationService;
    private final DOMSchemaService domSchemaService;
    private final TransactionChainHandler transactionChainHandler;
    private final DOMDataBroker dataBroker;
    private final SchemaContextHandler schemaCtxHandler;
    private final DOMMountPointServiceHandler mountPointServiceHandler;
    private final T wrapperServices;

    public RestConnectorProvider(final DOMDataBroker domDataBroker, final DOMSchemaService domSchemaService,
            final DOMRpcService rpcService, final DOMNotificationService notificationService,
            final TransactionChainHandler transactionChainHandler,
            final SchemaContextHandler schemaCtxHandler, final DOMMountPointServiceHandler mountPointServiceHandler,
            final T wrapperServices) {
        this.wrapperServices = wrapperServices;
        this.domSchemaService = Preconditions.checkNotNull(domSchemaService);
        this.rpcService = Preconditions.checkNotNull(rpcService);
        this.notificationService = Preconditions.checkNotNull(notificationService);
        this.transactionChainHandler = Preconditions.checkNotNull(transactionChainHandler);
        this.dataBroker = Preconditions.checkNotNull(domDataBroker);
        this.schemaCtxHandler = Preconditions.checkNotNull(schemaCtxHandler);
        this.mountPointServiceHandler = Preconditions.checkNotNull(mountPointServiceHandler);
    }

    public synchronized void start() {
        final DOMDataBrokerHandler brokerHandler = new DOMDataBrokerHandler(dataBroker);

        final RpcServiceHandler rpcServiceHandler = new RpcServiceHandler(rpcService);

        final NotificationServiceHandler notificationServiceHandler =
                new NotificationServiceHandler(notificationService);

        if (wrapperServices != null) {
            wrapperServices.setHandlers(this.schemaCtxHandler, mountPointServiceHandler,
                    transactionChainHandler, brokerHandler, rpcServiceHandler,
                    notificationServiceHandler, domSchemaService);
        }
    }

    @Override
    public void close() {
    }
}
