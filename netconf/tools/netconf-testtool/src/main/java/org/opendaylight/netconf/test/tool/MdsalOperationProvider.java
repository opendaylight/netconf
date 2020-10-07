/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool;

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.broker.SerializedDOMDataBroker;
import org.opendaylight.mdsal.dom.spi.store.DOMStore;
import org.opendaylight.mdsal.dom.store.inmemory.InMemoryDOMDataStoreFactory;
import org.opendaylight.netconf.api.capability.Capability;
import org.opendaylight.netconf.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.impl.SessionIdProvider;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.opendaylight.netconf.mdsal.connector.ops.Commit;
import org.opendaylight.netconf.mdsal.connector.ops.DiscardChanges;
import org.opendaylight.netconf.mdsal.connector.ops.EditConfig;
import org.opendaylight.netconf.mdsal.connector.ops.Lock;
import org.opendaylight.netconf.mdsal.connector.ops.Unlock;
import org.opendaylight.netconf.mdsal.connector.ops.get.Get;
import org.opendaylight.netconf.mdsal.connector.ops.get.GetConfig;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.Yang;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.Schema;
import org.opendaylight.yangtools.concepts.AbstractListenerRegistration;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.util.concurrent.SpecialExecutors;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContextListener;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MdsalOperationProvider implements NetconfOperationServiceFactory {

    private static final Logger LOG = LoggerFactory
            .getLogger(MdsalOperationProvider.class);

    private final Set<Capability> caps;
    private final EffectiveModelContext schemaContext;
    private final SchemaSourceProvider<YangTextSchemaSource> sourceProvider;

    MdsalOperationProvider(final SessionIdProvider idProvider,
                           final Set<Capability> caps,
                           final EffectiveModelContext schemaContext,
                           final SchemaSourceProvider<YangTextSchemaSource> sourceProvider) {
        this.caps = caps;
        this.schemaContext = schemaContext;
        this.sourceProvider = sourceProvider;
    }

    @Override
    public Set<Capability> getCapabilities() {
        return caps;
    }

    @Override
    public AutoCloseable registerCapabilityListener(
            final CapabilityListener listener) {
        listener.onCapabilitiesChanged(caps, Collections.emptySet());
        return () -> {
        };
    }

    @Override
    public NetconfOperationService createService(final String netconfSessionIdForReporting) {
        return new MdsalOperationService(Long.parseLong(netconfSessionIdForReporting), schemaContext,
            caps, sourceProvider);
    }

    static class MdsalOperationService implements NetconfOperationService {
        private final long currentSessionId;
        private final EffectiveModelContext schemaContext;
        private final Set<Capability> caps;
        private final DOMSchemaService schemaService;
        private final DOMDataBroker dataBroker;
        private final SchemaSourceProvider<YangTextSchemaSource> sourceProvider;

        MdsalOperationService(final long currentSessionId,
                              final EffectiveModelContext schemaContext,
                              final Set<Capability> caps,
                              final SchemaSourceProvider<YangTextSchemaSource> sourceProvider) {
            this.currentSessionId = currentSessionId;
            this.schemaContext = schemaContext;
            this.caps = caps;
            this.sourceProvider = sourceProvider;
            this.schemaService = createSchemaService();

            this.dataBroker = createDataStore(schemaService, currentSessionId);

        }

        @Override
        public Set<NetconfOperation> getNetconfOperations() {
            TransactionProvider transactionProvider = new TransactionProvider(
                dataBroker, String.valueOf(currentSessionId));
            CurrentSchemaContext currentSchemaContext = new CurrentSchemaContext(schemaService, sourceProvider);

            ContainerNode netconf = createNetconfState();

            YangInstanceIdentifier yangInstanceIdentifier = YangInstanceIdentifier.builder().node(NetconfState.QNAME)
                    .build();

            final DOMDataTreeWriteTransaction tx = dataBroker.newWriteOnlyTransaction();
            tx.put(LogicalDatastoreType.OPERATIONAL, yangInstanceIdentifier, netconf);

            try {
                tx.commit().get();
                LOG.debug("Netconf state updated successfully");
            } catch (InterruptedException | ExecutionException e) {
                LOG.warn("Unable to update netconf state", e);
            }

            final Get get = new Get(String.valueOf(currentSessionId), currentSchemaContext, transactionProvider);
            final EditConfig editConfig = new EditConfig(String.valueOf(currentSessionId), currentSchemaContext,
                    transactionProvider);
            final GetConfig getConfig = new GetConfig(String.valueOf(currentSessionId), currentSchemaContext,
                    transactionProvider);
            final Commit commit = new Commit(String.valueOf(currentSessionId), transactionProvider);
            final Lock lock = new Lock(String.valueOf(currentSessionId));
            final Unlock unLock = new Unlock(String.valueOf(currentSessionId));
            final DiscardChanges discardChanges = new DiscardChanges(
                String.valueOf(currentSessionId), transactionProvider);

            return Sets.newHashSet(get, getConfig,
                    editConfig, commit, lock, unLock, discardChanges);
        }

        @Override
        public void close() {
        }

        private ContainerNode createNetconfState() {
            DummyMonitoringService monitor = new DummyMonitoringService(
                    caps);

            final QName identifier = QName.create(Schema.QNAME, "identifier");
            final QName version = QName.create(Schema.QNAME, "version");
            final QName format = QName.create(Schema.QNAME, "format");
            final QName location = QName.create(Schema.QNAME, "location");
            final QName namespace = QName.create(Schema.QNAME, "namespace");

            CollectionNodeBuilder<MapEntryNode, MapNode> schemaMapEntryNodeMapNodeCollectionNodeBuilder = Builders
                    .mapBuilder().withNodeIdentifier(new NodeIdentifier(Schema.QNAME));
            LeafSetEntryNode locationLeafSetEntryNode = Builders.leafSetEntryBuilder().withNodeIdentifier(
                            new NodeWithValue<>(location, "NETCONF")).withValue("NETCONF").build();

            Map<QName, Object> keyValues = new HashMap<>();
            for (final Schema schema : monitor.getSchemas().getSchema().values()) {
                keyValues.put(identifier, schema.getIdentifier());
                keyValues.put(version, schema.getVersion());
                keyValues.put(format, Yang.QNAME);

                MapEntryNode schemaMapEntryNode = Builders.mapEntryBuilder()
                        .withNodeIdentifier(NodeIdentifierWithPredicates.of(Schema.QNAME, keyValues))
                        .withChild(Builders.leafBuilder().withNodeIdentifier(new NodeIdentifier(identifier))
                            .withValue(schema.getIdentifier()).build())
                        .withChild(Builders.leafBuilder().withNodeIdentifier(new NodeIdentifier(version))
                            .withValue(schema.getVersion()).build())
                        .withChild(Builders.leafBuilder().withNodeIdentifier(new NodeIdentifier(format))
                            .withValue(Yang.QNAME).build())
                        .withChild(Builders.leafBuilder().withNodeIdentifier(new NodeIdentifier(namespace))
                            .withValue(schema.getNamespace().getValue()).build())
                        .withChild((DataContainerChild<?, ?>) Builders.leafSetBuilder().withNodeIdentifier(
                                        new NodeIdentifier(location))
                                .withChild(locationLeafSetEntryNode).build())
                        .build();

                schemaMapEntryNodeMapNodeCollectionNodeBuilder.withChild(schemaMapEntryNode);
            }

            DataContainerChild<?, ?> schemaList = schemaMapEntryNodeMapNodeCollectionNodeBuilder.build();

            ContainerNode schemasContainer = Builders.containerBuilder().withNodeIdentifier(
                    new NodeIdentifier(Schemas.QNAME)).withChild(schemaList).build();
            return Builders.containerBuilder().withNodeIdentifier(
                    new NodeIdentifier(NetconfState.QNAME)).withChild(schemasContainer).build();
        }

        private static DOMDataBroker createDataStore(final DOMSchemaService schemaService, final long sessionId) {
            LOG.debug("Session {}: Creating data stores for simulated device", sessionId);
            final DOMStore operStore = InMemoryDOMDataStoreFactory.create("DOM-OPER", schemaService);
            final DOMStore configStore = InMemoryDOMDataStoreFactory.create("DOM-CFG", schemaService);

            ExecutorService listenableFutureExecutor = SpecialExecutors.newBlockingBoundedCachedThreadPool(
                    16, 16, "CommitFutures", MdsalOperationProvider.class);

            final EnumMap<LogicalDatastoreType, DOMStore> datastores = new EnumMap<>(LogicalDatastoreType.class);
            datastores.put(LogicalDatastoreType.CONFIGURATION, configStore);
            datastores.put(LogicalDatastoreType.OPERATIONAL, operStore);

            return new SerializedDOMDataBroker(datastores, MoreExecutors.listeningDecorator(listenableFutureExecutor));
        }

        private DOMSchemaService createSchemaService() {
            return new DOMSchemaService() {
                @Override
                public EffectiveModelContext getGlobalContext() {
                    return schemaContext;
                }

                @Override
                public ListenerRegistration<EffectiveModelContextListener> registerSchemaContextListener(
                        final EffectiveModelContextListener listener) {
                    listener.onModelContextUpdated(getGlobalContext());
                    return new AbstractListenerRegistration<>(listener) {
                        @Override
                        protected void removeRegistration() {
                            // No-op
                        }
                    };
                }
            };
        }
    }

}
