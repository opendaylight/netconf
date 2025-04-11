/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.remote.impl;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataBroker.DataTreeChangeExtension;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.DatabindPath;
import org.opendaylight.netconf.databind.DatabindProvider;
import org.opendaylight.netconf.databind.ErrorMessage;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.server.api.testlib.CompletingServerRequest;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfStreamRegistry;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.streams.stream.Access;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateDataChangeEventSubscription;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateDataChangeEventSubscriptionOutput;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.RpcEffectiveStatement;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@ExtendWith(MockitoExtension.class)
class CreateDataChangeEventSubscriptionRpcTest {
    private static final EffectiveModelContext SCHEMA_CTX = YangParserTestUtils.parseYangResourceDirectory("/streams");
    private static final URI RESTCONF_URI = URI.create("/rests/");
    private static final YangInstanceIdentifier TOASTER = YangInstanceIdentifier.of(
        QName.create("http://netconfcentral.org/ns/toaster", "2009-11-20", "toaster"));
    private static final String TEST_STREAMS = "test-streams";

    private final CompletingServerRequest<ContainerNode> request = new CompletingServerRequest<>();

    @Mock
    private DOMDataBroker dataBroker;
    @Mock
    private DOMNotificationService notificationService;
    @Mock
    private DataTreeChangeExtension treeChange;
    @Mock
    private DOMDataTreeWriteTransaction tx;
    @Mock
    private DOMSchemaService schemaService;
    @Captor
    private ArgumentCaptor<YangInstanceIdentifier> pathCaptor;
    @Captor
    private ArgumentCaptor<NormalizedNode> dataCaptor;

    private DatabindProvider databindProvider;

    private CreateDataChangeEventSubscriptionRpc rpc;

    @BeforeEach
    public void before() {
        databindProvider = () -> DatabindContext.ofModel(SCHEMA_CTX);

        doReturn(List.of(treeChange)).when(dataBroker).supportedExtensions();
        doReturn(SCHEMA_CTX).when(schemaService).getGlobalContext();
        doCallRealMethod().when(dataBroker).extension(any());
        rpc = new CreateDataChangeEventSubscriptionRpc(new MdsalRestconfStreamRegistry(dataBroker, notificationService,
            schemaService, restconfURI -> restconfURI.resolve(TEST_STREAMS)), databindProvider, dataBroker);
    }

    @Test
    void createStreamTest() throws Exception {
        doReturn(tx).when(dataBroker).newWriteOnlyTransaction();
        doNothing().when(tx).put(eq(LogicalDatastoreType.OPERATIONAL), pathCaptor.capture(), dataCaptor.capture());
        doReturn(CommitInfo.emptyFluentFuture()).when(tx).commit();

        rpc.invoke(request, RESTCONF_URI, createInput("path", TOASTER));

        final var output = request.getResult();
        assertEquals(new NodeIdentifier(CreateDataChangeEventSubscriptionOutput.QNAME), output.name());
        assertEquals(1, output.size());

        final var streamName = assertInstanceOf(LeafNode.class,
            output.childByArg(new NodeIdentifier(
                QName.create(CreateDataChangeEventSubscriptionOutput.QNAME, "stream-name"))));
        final var name = assertInstanceOf(String.class, streamName.body());
        assertEquals(45, name.length());
        assertThat(name, startsWith("urn:uuid:"));
        assertNotNull(UUID.fromString(name.substring(9)));

        final var rcStream = QName.create("urn:ietf:params:xml:ns:yang:ietf-restconf-monitoring", "2017-01-26",
            "stream");
        final var rcName = QName.create(rcStream, "name");
        final var streamId = NodeIdentifierWithPredicates.of(rcStream, rcName, name);
        final var rcEncoding = QName.create(rcStream, "encoding");
        final var rcLocation = QName.create(rcStream, "location");

        assertEquals(YangInstanceIdentifier.of(
            new NodeIdentifier(QName.create(rcStream, "restconf-state")),
            new NodeIdentifier(QName.create(rcStream, "streams")),
            new NodeIdentifier(rcStream),
            streamId), pathCaptor.getValue());
        assertEquals(ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(streamId)
            .withChild(ImmutableNodes.leafNode(rcName, name))
            .withChild(ImmutableNodes.leafNode(QName.create(rcStream, "description"),
                "Events occuring in CONFIGURATION datastore under /toaster:toaster"))
            .withChild(ImmutableNodes.newSystemMapBuilder()
                .withNodeIdentifier(new NodeIdentifier(Access.QNAME))
                .withChild(ImmutableNodes.newMapEntryBuilder()
                    .withNodeIdentifier(NodeIdentifierWithPredicates.of(Access.QNAME, rcEncoding, "json"))
                    .withChild(ImmutableNodes.leafNode(rcEncoding, "json"))
                    .withChild(ImmutableNodes.leafNode(rcLocation, "/rests/" + TEST_STREAMS + "/json/" + name))
                    .build())
                .withChild(ImmutableNodes.newMapEntryBuilder()
                    .withNodeIdentifier(NodeIdentifierWithPredicates.of(Access.QNAME, rcEncoding, "xml"))
                    .withChild(ImmutableNodes.leafNode(rcEncoding, "xml"))
                    .withChild(ImmutableNodes.leafNode(rcLocation, "/rests/" + TEST_STREAMS + "/xml/" + name))
                    .build())
                .build())
            .build().prettyTree().toString(), dataCaptor.getValue().prettyTree().toString());
    }

    @Test
    void createStreamWrongValueTest() {
        rpc.invoke(request, RESTCONF_URI, createInput("path", "String value"));

        final var ex = assertThrows(RequestException.class, request::getResult);
        final var errors = ex.errors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals(ErrorType.APPLICATION, error.type());
        assertEquals(ErrorTag.BAD_ELEMENT, error.tag());
        assertEquals(new ErrorMessage("""
            Bad child leafNode (urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote@2014-01-14)path = \
            "String value"\
            """), error.message());
    }

    @Test
    void createStreamWrongInputRpcTest() {
        rpc.invoke(request, RESTCONF_URI, createInput(null, null));

        final var ex = assertThrows(RequestException.class, request::getResult);
        final var errors = ex.errors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals(ErrorType.APPLICATION, error.type());
        assertEquals(ErrorTag.MISSING_ELEMENT, error.tag());
        assertEquals(new ErrorMessage("missing path"), error.message());
    }

    private OperationInput createInput(final @Nullable String leafName, final Object leafValue) {
        final var stack = SchemaInferenceStack.of(SCHEMA_CTX);
        final var rpcStmt = assertInstanceOf(RpcEffectiveStatement.class,
            stack.enterSchemaTree(CreateDataChangeEventSubscription.QNAME));
        final var inference = stack.toInference();

        final var builder = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(rpcStmt.input().argument()));
        if (leafName != null) {
            final var lfQName = QName.create(rpcStmt.argument(), leafName);
            stack.enterDataTree(rpcStmt.input().argument());
            stack.enterDataTree(lfQName);
            builder.withChild(ImmutableNodes.leafNode(lfQName, leafValue));
        }
        return new OperationInput(new DatabindPath.Rpc(databindProvider.currentDatabind(), inference, rpcStmt),
            builder.build());
    }
}
