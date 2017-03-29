/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.testtool.datastore;

import com.google.common.util.concurrent.MoreExecutors;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.broker.impl.SerializedDOMDataBroker;
import org.opendaylight.controller.md.sal.dom.store.impl.InMemoryDOMDataStoreFactory;
import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.controller.sal.core.spi.data.DOMStore;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.util.concurrent.SpecialExecutors;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;

public class Datastore {

    private final SchemaContext schemaContext;
    private final DOMDataBroker dataBroker;

    public Datastore(final SchemaContext schemaContext) {
        this.schemaContext = schemaContext;
        final SchemaService schemaService = createSchemaService();
        this.dataBroker = createDataStore(schemaService);
    }

    private DOMDataBroker createDataStore(final SchemaService schemaService) {
        final DOMStore operStore = InMemoryDOMDataStoreFactory
                .create("DOM-OPER", schemaService);
        final DOMStore configStore = InMemoryDOMDataStoreFactory
                .create("DOM-CFG", schemaService);

        final ExecutorService listenableFutureExecutor = SpecialExecutors.newBlockingBoundedCachedThreadPool(
                16, 16, "CommitFutures");

        final Map<LogicalDatastoreType, DOMStore> datastores = new EnumMap<>(LogicalDatastoreType.class);
        datastores.put(LogicalDatastoreType.CONFIGURATION, configStore);
        datastores.put(LogicalDatastoreType.OPERATIONAL, operStore);

        return new SerializedDOMDataBroker(datastores, MoreExecutors.listeningDecorator(listenableFutureExecutor));
    }

    public DOMDataBroker getDataBroker() {
        return dataBroker;
    }

    public SchemaContext getSchemaContext() {
        return schemaContext;
    }

    private SchemaService createSchemaService() {
        return new SchemaServiceCreator(schemaContext);
    }

    private static class SchemaServiceCreator implements SchemaService {

        private final SchemaContext schema;

        public SchemaServiceCreator(final SchemaContext schema) {
            this.schema = schema;
        }

        @Override
        public void addModule(final Module module) {
        }

        @Override
        public void removeModule(final Module module) {

        }

        @Override
        public SchemaContext getSessionContext() {
            return schema;
        }

        @Override
        public SchemaContext getGlobalContext() {
            return schema;
        }

        @Override
        public ListenerRegistration<SchemaContextListener> registerSchemaContextListener(
                final SchemaContextListener listener) {
            listener.onGlobalContextUpdated(getGlobalContext());
            return new ListenerRegistration<SchemaContextListener>() {
                @Override
                public void close() {

                }

                @Override
                public SchemaContextListener getInstance() {
                    return listener;
                }
            };
        }
    }

}

