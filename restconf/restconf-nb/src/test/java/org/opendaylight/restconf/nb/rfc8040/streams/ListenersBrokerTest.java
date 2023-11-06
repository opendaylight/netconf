/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import java.net.URI;
import java.util.UUID;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
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
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.streams.stream.Access;
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
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.ContainerLike;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@ExtendWith(MockitoExtension.class)
class ListenersBrokerTest {
    private static final EffectiveModelContext SCHEMA_CTX = YangParserTestUtils.parseYangResourceDirectory("/streams");

    @Mock
    private DOMDataBroker dataBroker;
    @Mock
    private DOMDataTreeWriteTransaction tx;
    @Mock
    private UriInfo uriInfo;
    @Mock
    private UriBuilder uriBuilder;
    @Captor
    private ArgumentCaptor<String> uriCaptor;
    @Captor
    private ArgumentCaptor<YangInstanceIdentifier> pathCaptor;
    @Captor
    private ArgumentCaptor<NormalizedNode> dataCaptor;

    private ListenersBroker listenersBroker;
    private DatabindProvider databindProvider;

    @BeforeEach
    public void before() {
        listenersBroker = new ListenersBroker.ServerSentEvents(dataBroker);
        databindProvider = () -> DatabindContext.ofModel(SCHEMA_CTX);
    }

    @Test
    void createStreamTest() {
        doReturn(tx).when(dataBroker).newWriteOnlyTransaction();
        doNothing().when(tx).put(eq(LogicalDatastoreType.OPERATIONAL), pathCaptor.capture(), dataCaptor.capture());
        doReturn(CommitInfo.emptyFluentFuture()).when(tx).commit();

        doReturn(uriBuilder).when(uriInfo).getBaseUriBuilder();
        doReturn(uriBuilder).when(uriBuilder).replacePath(uriCaptor.capture());
        doAnswer(inv -> new URI(uriCaptor.getValue())).when(uriBuilder).build();

        final var output = assertInstanceOf(ContainerNode.class,
            listenersBroker.createDataChangeNotifiStream(databindProvider, new URI("baseURI"),
                prepareDomPayload("create-data-change-event-subscription", "toaster", "path"), SCHEMA_CTX)
            .getOrThrow()
            .orElse(null));

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

        assertEquals(YangInstanceIdentifier.of(
            new NodeIdentifier(QName.create(rcStream, "restconf-state")),
            new NodeIdentifier(QName.create(rcStream, "streams")),
            new NodeIdentifier(rcStream),
            streamId), pathCaptor.getValue());
        assertEquals(Builders.mapEntryBuilder()
            .withNodeIdentifier(streamId)
            .withChild(ImmutableNodes.leafNode(rcName, name))
            .withChild(ImmutableNodes.leafNode(QName.create(rcStream, "description"),
                "Events occuring in CONFIGURATION datastore under /toaster:toaster"))
            .withChild(Builders.mapBuilder()
                .withNodeIdentifier(new NodeIdentifier(Access.QNAME))
                .withChild(Builders.mapEntryBuilder()
                    .withNodeIdentifier(NodeIdentifierWithPredicates.of(Access.QNAME, rcEncoding, ""))
                    .withChild(ImmutableNodes.leafNode(rcEncoding, ""))
                    .withChild(ImmutableNodes.leafNode(QName.create(rcStream, "location"),
                        "rests/streams/" + name))
                    .build())
                .build())
            .build(), dataCaptor.getValue());
    }

    @Test
    void createStreamWrongValueTest() {
        final var payload = prepareDomPayload("create-data-change-event-subscription", "String value", "path");
        final var errors = assertThrows(RestconfDocumentedException.class,
            () -> listenersBroker.createDataChangeNotifiStream(databindProvider, uriInfo, payload, SCHEMA_CTX))
            .getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals(ErrorType.APPLICATION, error.getErrorType());
        assertEquals(ErrorTag.OPERATION_FAILED, error.getErrorTag());
        assertEquals("Instance identifier was not normalized correctly", error.getErrorMessage());
    }

    @Test
    void createStreamWrongInputRpcTest() {
        final var payload = prepareDomPayload("create-data-change-event-subscription2", "toaster", "path2");
        final var errors = assertThrows(RestconfDocumentedException.class,
            () -> listenersBroker.createDataChangeNotifiStream(databindProvider, uriInfo, payload, SCHEMA_CTX))
            .getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals(ErrorType.APPLICATION, error.getErrorType());
        assertEquals(ErrorTag.OPERATION_FAILED, error.getErrorTag());
        assertEquals("Instance identifier was not normalized correctly", error.getErrorMessage());
    }

    private static ContainerNode prepareDomPayload(final String rpcName, final String toasterValue,
            final String inputOutputName) {
        final var rpcModule = SCHEMA_CTX.findModules("sal-remote").iterator().next();
        final QName rpcQName = QName.create(rpcModule.getQNameModule(), rpcName);

        ContainerLike containerSchemaNode = null;
        for (final RpcDefinition rpc : rpcModule.getRpcs()) {
            if (rpcQName.isEqualWithoutRevision(rpc.getQName())) {
                containerSchemaNode = rpc.getInput();
                break;
            }
        }
        assertNotNull(containerSchemaNode);

        final QName lfQName = QName.create(rpcModule.getQNameModule(), inputOutputName);
        assertInstanceOf(LeafSchemaNode.class, containerSchemaNode.dataChildByName(lfQName));

        final Object o;
        if ("toaster".equals(toasterValue)) {
            final QName rpcQname = QName.create("http://netconfcentral.org/ns/toaster", "2009-11-20", toasterValue);
            o = YangInstanceIdentifier.of(rpcQname);
        } else {
            o = toasterValue;
        }

        return Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(containerSchemaNode.getQName()))
            .withChild(ImmutableNodes.leafNode(lfQName, o))
            .build();
    }
}
