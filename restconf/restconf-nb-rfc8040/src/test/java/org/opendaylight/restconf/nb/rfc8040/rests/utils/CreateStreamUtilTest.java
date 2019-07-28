/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Lists;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.MonitoringModule;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.references.SchemaContextRef;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.BaseListenerInterface;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenerAdapter;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenersBroker;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableMapEntryNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableMapNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.util.SchemaNodeUtils;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class CreateStreamUtilTest {

    private static final String PATH_FOR_NEW_SCHEMA_CONTEXT = "/streams";
    private static final String PATH_TO_EXAMPLE_NOTIFICATIONS = "/modules/example-notifications.yang";

    private NormalizedNodeContext payload;
    private SchemaContextRef refSchemaCtx;

    @Mock
    private TransactionChainHandler transactionChainHandler;
    @Mock
    private DOMTransactionChain domTransactionChain;

    @Before
    public void setUp() throws Exception {
        final Collection<File> yangFiles = TestRestconfUtils.loadFiles(PATH_FOR_NEW_SCHEMA_CONTEXT);
        yangFiles.add(new File(getClass().getResource(PATH_TO_EXAMPLE_NOTIFICATIONS).getPath()));
        this.refSchemaCtx = new SchemaContextRef(YangParserTestUtils.parseYangFiles(yangFiles));
        Mockito.when(transactionChainHandler.get()).thenReturn(domTransactionChain);
    }

    @Test
    public void createDataChangeStreamTest() {
        final DOMDataTreeWriteTransaction transaction = Mockito.mock(DOMDataTreeWriteTransaction.class);
        Mockito.when(domTransactionChain.newWriteOnlyTransaction()).thenReturn(transaction);
        Mockito.doReturn(FluentFutures.immediateFluentFuture(CommitInfo.empty())).when(transaction).commit();

        this.payload = prepareDomPayload("create-data-change-event-subscription", "input", "toaster", "path");
        final DOMRpcResult result = CreateStreamUtil.createDataChangeNotifiStream(this.payload, this.refSchemaCtx,
                transactionChainHandler);
        assertEquals(result.getErrors(), Collections.emptyList());
        final NormalizedNode<?, ?> testedNn = result.getResult();
        assertNotNull(testedNn);
        final NormalizedNodeContext contextRef = prepareDomPayload("create-data-change-event-subscription", "output",
                "data-change-event-subscription/toaster:toaster/datastore=CONFIGURATION/scope=BASE", "stream-name");
        assertEquals(contextRef.getData(), testedNn);
        final Optional<ListenerAdapter> dataChangeListener = ListenersBroker.getInstance().getDataChangeListenerFor(
                "data-change-event-subscription/toaster:toaster/datastore=CONFIGURATION/scope=BASE");
        Assert.assertTrue(dataChangeListener.isPresent());

        final ArgumentCaptor<NormalizedNode> dataCaptor = ArgumentCaptor.forClass(NormalizedNode.class);
        Mockito.verify(transaction, Mockito.times(1)).merge(
                Mockito.eq(LogicalDatastoreType.OPERATIONAL), Mockito.eq(CreateStreamUtil.STREAMS_YIID),
                dataCaptor.capture());
        Mockito.verify(transaction, Mockito.times(1)).commit();
        assertEquals(MonitoringModule.CONT_STREAMS_QNAME, dataCaptor.getValue().getNodeType());
    }

    @Test(expected = RestconfDocumentedException.class)
    public void createDataChangeStreamWrongValueTest() {
        this.payload = prepareDomPayload("create-data-change-event-subscription", "input", "String value", "path");
        final DOMRpcResult result = CreateStreamUtil.createDataChangeNotifiStream(this.payload, this.refSchemaCtx,
                transactionChainHandler);
        assertEquals(result.getErrors(), Collections.emptyList());
        Mockito.verifyZeroInteractions(transactionChainHandler);
    }

    @Test(expected = RestconfDocumentedException.class)
    public void createDataChangeStreamWrongInputRpcTest() {
        this.payload = prepareDomPayload("create-data-change-event-subscription2", "input", "toaster", "path2");
        final DOMRpcResult result = CreateStreamUtil.createDataChangeNotifiStream(this.payload, this.refSchemaCtx,
                transactionChainHandler);
        assertEquals(result.getErrors(), Collections.emptyList());
        Mockito.verifyZeroInteractions(transactionChainHandler);
    }

    @Test
    public void createAllYangNotifiStreamsTest() {
        // preparation of input and expected data
        final DOMDataTreeReadWriteTransaction rwTransaction = Mockito.mock(DOMDataTreeReadWriteTransaction.class);
        final NormalizedNode<?, ?> existingStreams = ImmutableContainerNodeBuilder.create()
                .withNodeIdentifier(NodeIdentifier.create(MonitoringModule.CONT_STREAMS_QNAME))
                .withChild(ImmutableMapNodeBuilder.create()
                        .withNodeIdentifier(NodeIdentifier.create(MonitoringModule.LIST_STREAM_QNAME))
                        .withValue(Collections.emptyList())
                        .build())
                .build();
        Mockito.when(rwTransaction.read(LogicalDatastoreType.OPERATIONAL, CreateStreamUtil.STREAMS_YIID))
                .thenReturn(FluentFutures.immediateFluentFuture(Optional.of(existingStreams)));

        final List<String> expectedXmlStreams = Lists.newArrayList(
                "notification-stream/sal-remote:data-changed-notification",
                "notification-stream/example-notifications:root-notify",
                "notification-stream/example-notifications:example-container/notification-under-container",
                "notification-stream/toaster:toastDone",
                "notification-stream/example-notifications:example-container/sub-list/sub-notification");
        final List<String> expectedJsonStreams = expectedXmlStreams.stream()
                .map(s -> s + "/JSON")
                .collect(Collectors.toList());

        // execution of mapping and testing of the output container
        CreateStreamUtil.createYangNotifiStreams(this.refSchemaCtx.get(), rwTransaction);
        checkRegisteredYangNotifyStreams(rwTransaction, expectedXmlStreams, expectedJsonStreams);
    }

    @Test
    public void createOnlyNewYangNotifiStreamsTest() {
        // preparation of input and expected data
        final DOMDataTreeReadWriteTransaction rwTransaction = Mockito.mock(DOMDataTreeReadWriteTransaction.class);
        final List<String> existingStreamNames = Lists.newArrayList(
                "/sal-remote:data-changed-notification", "/example-notifications:root-notify");
        final NormalizedNode<?, ?> existingStreams = ImmutableContainerNodeBuilder.create()
                .withNodeIdentifier(NodeIdentifier.create(MonitoringModule.CONT_STREAMS_QNAME))
                .withChild(ImmutableMapNodeBuilder.create()
                        .withNodeIdentifier(NodeIdentifier.create(MonitoringModule.LIST_STREAM_QNAME))
                        .withValue(existingStreamNames.stream()
                                .map(streamName -> ImmutableMapEntryNodeBuilder.create()
                                        .withNodeIdentifier(NodeIdentifierWithPredicates.of(
                                                MonitoringModule.LIST_STREAM_QNAME,
                                                MonitoringModule.LEAF_NAME_STREAM_QNAME, streamName))
                                        .build())
                                .collect(Collectors.toList()))
                        .build())
                .build();
        Mockito.when(rwTransaction.read(LogicalDatastoreType.OPERATIONAL, CreateStreamUtil.STREAMS_YIID))
                .thenReturn(FluentFutures.immediateFluentFuture(Optional.of(existingStreams)));

        final List<String> expectedXmlStreams = Lists.newArrayList(
                "notification-stream/example-notifications:example-container/notification-under-container",
                "notification-stream/toaster:toastDone",
                "notification-stream/example-notifications:example-container/sub-list/sub-notification");
        final List<String> expectedJsonStreams = expectedXmlStreams.stream()
                .map(s -> s + "/JSON")
                .collect(Collectors.toList());

        // execution of mapping and testing of the output container
        CreateStreamUtil.createYangNotifiStreams(this.refSchemaCtx.get(), rwTransaction);
        checkRegisteredYangNotifyStreams(rwTransaction, expectedXmlStreams, expectedJsonStreams);
    }

    private static void checkRegisteredYangNotifyStreams(final DOMDataTreeReadWriteTransaction rwTransaction,
            final List<String> expectedXmlStreams, final List<String> expectedJsonStreams) {
        checkListenerBroker(Stream.concat(expectedXmlStreams.stream(), expectedJsonStreams.stream())
                .collect(Collectors.toList()));
        final ArgumentCaptor<NormalizedNode> dataCaptor = ArgumentCaptor.forClass(NormalizedNode.class);
        Mockito.verify(rwTransaction, Mockito.never()).commit();
        Mockito.verify(rwTransaction, Mockito.times(1)).merge(
                Mockito.eq(LogicalDatastoreType.OPERATIONAL), Mockito.eq(CreateStreamUtil.STREAMS_YIID),
                dataCaptor.capture());
        assertEquals(MonitoringModule.CONT_STREAMS_QNAME, dataCaptor.getValue().getNodeType());
        assertEquals(1, ((ContainerNode) dataCaptor.getValue()).getValue().size());
        final Optional<DataContainerChild<? extends PathArgument, ?>> streamsListNode = ((ContainerNode) dataCaptor
                .getValue()).getChild(NodeIdentifier.create(MonitoringModule.LIST_STREAM_QNAME));
        assertTrue(streamsListNode.isPresent());
        assertEquals(expectedXmlStreams.size(), ((MapNode) streamsListNode.get()).getValue().size());
    }

    private static void checkListenerBroker(final List<String> expectedStreams) {
        expectedStreams.forEach(streamName -> {
            final Optional<BaseListenerInterface> listener = ListenersBroker.getInstance().getListenerFor(streamName);
            Assert.assertTrue(listener.isPresent());
        });
    }

    private NormalizedNodeContext prepareDomPayload(final String rpcName, final String inputOutput,
            final String toasterValue, final String inputOutputName) {
        final SchemaContext schema = this.refSchemaCtx.get();
        final Module rpcModule = schema.findModules("sal-remote").iterator().next();
        final QName rpcQName = QName.create(rpcModule.getQNameModule(), rpcName);
        final QName rpcInputQName = QName.create(rpcModule.getQNameModule(), inputOutput);
        final Set<RpcDefinition> setRpcs = rpcModule.getRpcs();
        ContainerSchemaNode rpcInputSchemaNode = null;
        for (final RpcDefinition rpc : setRpcs) {
            if (rpcQName.isEqualWithoutRevision(rpc.getQName())) {
                rpcInputSchemaNode = SchemaNodeUtils.getRpcDataSchema(rpc, rpcInputQName);
                break;
            }
        }
        assertNotNull(rpcInputSchemaNode);

        final DataContainerNodeBuilder<YangInstanceIdentifier.NodeIdentifier, ContainerNode> container =
                Builders.containerBuilder(rpcInputSchemaNode);

        final QName lfQName = QName.create(rpcModule.getQNameModule(), inputOutputName);
        final DataSchemaNode lfSchemaNode = rpcInputSchemaNode.getDataChildByName(lfQName);

        assertTrue(lfSchemaNode instanceof LeafSchemaNode);

        final Object o;
        if ("toaster".equals(toasterValue)) {
            final QName rpcQname = QName.create("http://netconfcentral.org/ns/toaster", "2009-11-20", toasterValue);
            o = YangInstanceIdentifier.builder().node(rpcQname).build();
        } else {
            o = toasterValue;
        }
        final LeafNode<Object> lfNode = Builders.leafBuilder((LeafSchemaNode) lfSchemaNode)
                .withValue(o).build();
        container.withChild(lfNode);

        return new NormalizedNodeContext(new InstanceIdentifierContext<>(null, rpcInputSchemaNode, null, schema),
                container.build());
    }
}
