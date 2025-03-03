/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool;

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService.YangTextSourceExtension;
import org.opendaylight.mdsal.dom.broker.SerializedDOMDataBroker;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.mdsal.dom.store.inmemory.InMemoryDOMDataStoreFactory;
import org.opendaylight.netconf.server.api.SessionIdProvider;
import org.opendaylight.netconf.server.api.monitoring.Capability;
import org.opendaylight.netconf.server.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.server.api.operations.NetconfOperation;
import org.opendaylight.netconf.server.api.operations.NetconfOperationService;
import org.opendaylight.netconf.server.api.operations.NetconfOperationServiceFactory;
import org.opendaylight.netconf.server.mdsal.CurrentSchemaContext;
import org.opendaylight.netconf.server.mdsal.TransactionProvider;
import org.opendaylight.netconf.server.mdsal.operations.Commit;
import org.opendaylight.netconf.server.mdsal.operations.DiscardChanges;
import org.opendaylight.netconf.server.mdsal.operations.EditConfig;
import org.opendaylight.netconf.server.mdsal.operations.Get;
import org.opendaylight.netconf.server.mdsal.operations.GetConfig;
import org.opendaylight.netconf.server.mdsal.operations.Lock;
import org.opendaylight.netconf.server.mdsal.operations.Unlock;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.Yang;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.Schema;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.util.concurrent.SpecialExecutors;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MdsalOperationProvider implements NetconfOperationServiceFactory {
    private static final Logger LOG = LoggerFactory.getLogger(MdsalOperationProvider.class);

    private final Set<Capability> caps;
    private final DOMSchemaService schemaService;

    MdsalOperationProvider(final SessionIdProvider idProvider, final Set<Capability> caps,
            final EffectiveModelContext modelContext, final YangTextSourceExtension sourceProvider) {
        this.caps = requireNonNull(caps);
        schemaService = new FixedDOMSchemaService(() -> modelContext, sourceProvider);
    }

    @Override
    public Set<Capability> getCapabilities() {
        return caps;
    }

    @Override
    public Registration registerCapabilityListener(final CapabilityListener listener) {
        listener.onCapabilitiesChanged(caps, Set.of());
        return () -> { };
    }

    @Override
    public NetconfOperationService createService(final SessionIdType sessionId) {
        return new MdsalOperationService(sessionId, schemaService, caps);
    }

    static class MdsalOperationService implements NetconfOperationService {
        private final SessionIdType currentSessionId;
        private final DOMSchemaService schemaService;
        private final Set<Capability> caps;
        private final DOMDataBroker dataBroker;

        MdsalOperationService(final SessionIdType currentSessionId, final DOMSchemaService schemaService,
                              final Set<Capability> caps) {
            this.currentSessionId = requireNonNull(currentSessionId);
            this.schemaService = requireNonNull(schemaService);
            this.caps = caps;

            dataBroker = createDataStore(schemaService, currentSessionId);
        }

        @Override
        public Set<NetconfOperation> getNetconfOperations() {
            ContainerNode netconf = createNetconfState();

            final DOMDataTreeWriteTransaction tx = dataBroker.newWriteOnlyTransaction();
            tx.put(LogicalDatastoreType.OPERATIONAL, YangInstanceIdentifier.of(NetconfState.QNAME), netconf);

            try {
                tx.commit().get();
                LOG.debug("Netconf state updated successfully");
            } catch (InterruptedException | ExecutionException e) {
                LOG.warn("Unable to update netconf state", e);
            }

            TransactionProvider transactionProvider = new TransactionProvider(dataBroker, currentSessionId);
            CurrentSchemaContext currentSchemaContext = new CurrentSchemaContext(schemaService);

            final Get get = new Get(currentSessionId, currentSchemaContext, transactionProvider);
            final EditConfig editConfig = new EditConfig(currentSessionId, currentSchemaContext, transactionProvider);
            final GetConfig getConfig = new GetConfig(currentSessionId, currentSchemaContext, transactionProvider);
            final Commit commit = new Commit(currentSessionId, transactionProvider);
            final Lock lock = new Lock(currentSessionId);
            final Unlock unLock = new Unlock(currentSessionId);
            final DiscardChanges discardChanges = new DiscardChanges(currentSessionId, transactionProvider);

            return Set.of(get, getConfig, editConfig, commit, lock, unLock, discardChanges);
        }

        @Override
        public void close() {
            // No-op
        }

        private ContainerNode createNetconfState() {
            final DummyMonitoringService monitor = new DummyMonitoringService(caps);
            final QName identifier = QName.create(Schema.QNAME, "identifier");
            final QName version = QName.create(Schema.QNAME, "version");
            final QName format = QName.create(Schema.QNAME, "format");
            final QName location = QName.create(Schema.QNAME, "location");
            final QName namespace = QName.create(Schema.QNAME, "namespace");

            final var schemaMapEntryNodeMapNodeCollectionNodeBuilder = ImmutableNodes.newSystemMapBuilder()
                .withNodeIdentifier(new NodeIdentifier(Schema.QNAME));
            final var locationLeafSet = ImmutableNodes.<String>newSystemLeafSetBuilder()
                .withNodeIdentifier(new NodeIdentifier(location))
                .withChildValue("NETCONF")
                .build();

            final var keyValues = new HashMap<QName, Object>();
            for (final Schema schema : monitor.getSchemas().nonnullSchema().values()) {
                keyValues.put(identifier, schema.getIdentifier());
                keyValues.put(version, schema.getVersion());
                keyValues.put(format, Yang.QNAME);

                schemaMapEntryNodeMapNodeCollectionNodeBuilder.withChild(ImmutableNodes.newMapEntryBuilder()
                    .withNodeIdentifier(NodeIdentifierWithPredicates.of(Schema.QNAME, keyValues))
                    .withChild(ImmutableNodes.leafNode(identifier, schema.getIdentifier()))
                    .withChild(ImmutableNodes.leafNode(version, schema.getVersion()))
                    .withChild(ImmutableNodes.leafNode(format, Yang.QNAME))
                    .withChild(ImmutableNodes.leafNode(namespace, schema.getNamespace().getValue()))
                    .withChild(locationLeafSet)
                    .build());
            }

            return ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(new NodeIdentifier(NetconfState.QNAME))
                .withChild(ImmutableNodes.newContainerBuilder()
                    .withNodeIdentifier(new NodeIdentifier(Schemas.QNAME))
                    .withChild(schemaMapEntryNodeMapNodeCollectionNodeBuilder.build())
                    .build())
                .build();
        }

        private static DOMDataBroker createDataStore(final DOMSchemaService schemaService,
                final SessionIdType sessionId) {
            LOG.debug("Session {}: Creating data stores for simulated device", sessionId.getValue());

            // FIXME: NETCONF-1414: Multiple problems from day one:
            //          - the executor is never cleaned up
            //          - the datastores are not closed
            //          - the executor has potentially multiple threads, leading to IMDS sequencing bugs
            //        We do not want to spawn a single-threaded executor for each client, so we need need something
            //        a tad smarter:
            //          - create a global EqualityQueuedNotificationManager with a flexible thread pool
            //          - the listener class is identity-based something
            //          - the notification class is Runnable
            //        And the here we perform:
            //          - allocate a listener instance
            //          - pass down 'runnable -> manager.submitNotification(listener, runnable)
            //        That way we get thread sharing across all devices, while still guaranteeing single-threaded
            //        view of things.
            final var configStore = InMemoryDOMDataStoreFactory.create("DOM-CFG", schemaService);
            final var operStore = InMemoryDOMDataStoreFactory.create("DOM-OPER", schemaService);
            final var listenableFutureExecutor = SpecialExecutors.newBlockingBoundedCachedThreadPool(16, 16,
                "CommitFutures", MdsalOperationProvider.class);

            return new SerializedDOMDataBroker(Map.of(
                LogicalDatastoreType.CONFIGURATION, configStore,
                LogicalDatastoreType.OPERATIONAL, operStore), listenableFutureExecutor);
        }
    }
}
