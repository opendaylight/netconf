/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.restconf.impl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.FileNotFoundException;
import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.rest.api.Draft02;
import org.opendaylight.netconf.sal.restconf.impl.BrokerFacade;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfImpl;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.netconf.sal.streams.websockets.WebSocketServer;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;

/**
 * See {@link InvokeRpcMethodTest}.
 */
public class RestconfImplTest {

    private static EffectiveModelContext schemaContext;

    private final BrokerFacade brokerFacade = mock(BrokerFacade.class);
    private final ControllerContext controllerContext = TestRestconfUtils.newControllerContext(schemaContext);
    private final RestconfImpl restconfImpl = RestconfImpl.newInstance(brokerFacade, controllerContext);

    @BeforeClass
    public static void init() throws FileNotFoundException, ReactorException {
        schemaContext = TestUtils.loadSchemaContext("/full-versions/yangs", "/modules/restconf-module-testing");
    }

    @AfterClass
    public static void cleanUp() {
        WebSocketServer.destroyInstance(); // NETCONF-604
    }

    @Test
    public void binaryKeyTest() {
        final List<Byte> al = new ArrayList<>();
        al.add((byte) 1);
        binaryKeyTest(al, al);
    }

    private static void binaryKeyTest(final List<Byte> al, final List<Byte> al2) {

        final QName keyDef = QName.create("test:key:binary", "2017-08-14", "b1");

        final Map<QName, Object> uriKeyValues = new HashMap<>();
        uriKeyValues.put(keyDef, al.toArray());

        final MapEntryNode payload = mock(MapEntryNode.class);
        final NodeIdentifierWithPredicates nodeIdWithPred =
                NodeIdentifierWithPredicates.of(keyDef, keyDef, al2.toArray());
        when(payload.getIdentifier()).thenReturn(nodeIdWithPred);

        final List<QName> keyDefinitions = new ArrayList<>();
        keyDefinitions.add(keyDef);
        RestconfImpl.isEqualUriAndPayloadKeyValues(uriKeyValues, payload, keyDefinitions);
    }

    @Test
    public void binaryKeyFailTest() {
        final List<Byte> al = new ArrayList<>();
        al.add((byte) 1);
        final List<Byte> al2 = new ArrayList<>();
        try {
            binaryKeyTest(al, al2);
        } catch (final RestconfDocumentedException e) {
            final RestconfError err = e.getErrors().iterator().next();
            assertEquals(ErrorType.PROTOCOL, err.getErrorType());
            assertEquals(ErrorTag.INVALID_VALUE, err.getErrorTag());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExample() throws FileNotFoundException, ParseException {
        @SuppressWarnings("rawtypes")
        final NormalizedNode normalizedNodeData = TestUtils.prepareNormalizedNodeWithIetfInterfacesInterfacesData();
        when(brokerFacade.readOperationalData(isNull())).thenReturn(normalizedNodeData);
        assertEquals(normalizedNodeData,
                brokerFacade.readOperationalData(null));
    }

    @Test
    public void testRpcForMountpoint() throws Exception {
        final UriInfo uriInfo = mock(UriInfo.class);
        doReturn(new MultivaluedHashMap<>()).when(uriInfo).getQueryParameters(anyBoolean());

        final NormalizedNodeContext ctx = mock(NormalizedNodeContext.class);
        final InstanceIdentifierContext<?> iiCtx = mock(InstanceIdentifierContext.class);
        doReturn(iiCtx).when(ctx).getInstanceIdentifierContext();
        final SchemaNode schemaNode = mock(SchemaNode.class);
        doReturn(schemaNode).when(iiCtx).getSchemaNode();
        doReturn(mock(SchemaPath.class)).when(schemaNode).getPath();
        doReturn(QName.create("namespace", "2010-10-10", "localname")).when(schemaNode).getQName();

        final DOMMountPoint mount = mock(DOMMountPoint.class);
        doReturn(mount).when(iiCtx).getMountPoint();
        final DOMRpcService rpcService = mock(DOMRpcService.class);
        doReturn(Optional.of(rpcService)).when(mount).getService(DOMRpcService.class);
        doReturn(immediateFluentFuture(mock(DOMRpcResult.class))).when(rpcService)
                .invokeRpc(any(QName.class), any(NormalizedNode.class));
        this.restconfImpl.invokeRpc("randomId", ctx, uriInfo);
        this.restconfImpl.invokeRpc("ietf-netconf", ctx, uriInfo);
        verify(rpcService, times(2)).invokeRpc(any(QName.class), any());
    }

    /**
     * Create notification stream for toaster module.
     */
    @Test
    public void createNotificationStreamTest() {
        final NormalizedNodeContext payload = mock(NormalizedNodeContext.class);
        final InstanceIdentifierContext<?> iiCtx = mock(InstanceIdentifierContext.class);
        doReturn(iiCtx).when(payload).getInstanceIdentifierContext();

        final SchemaNode schemaNode = mock(SchemaNode.class,
                Mockito.withSettings().extraInterfaces(RpcDefinition.class));
        doReturn(schemaNode).when(iiCtx).getSchemaNode();
        doReturn(mock(SchemaPath.class)).when(schemaNode).getPath();

        doReturn(QName.create("urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote",
                "2014-01-14", "create-notification-stream")).when(schemaNode).getQName();
        doReturn(null).when(iiCtx).getMountPoint();

        final Set<DataContainerChild<?, ?>> children = new HashSet<>();
        final DataContainerChild<?, ?> child = mock(DataContainerChild.class,
                Mockito.withSettings().extraInterfaces(LeafSetNode.class));

        final LeafSetEntryNode entryNode = mock(LeafSetEntryNode.class);
        when(entryNode.getValue()).thenReturn("(http://netconfcentral.org/ns/toaster?revision=2009-11-20)toastDone");
        when(((LeafSetNode) child).getValue()).thenReturn(Sets.newHashSet(entryNode));
        children.add(child);

        final NormalizedNode<?, ?> normalizedNode = mock(NormalizedNode.class,
                Mockito.withSettings().extraInterfaces(ContainerNode.class));
        doReturn(normalizedNode).when(payload).getData();
        doReturn(children).when(normalizedNode).getValue();

        // register notification
        final NormalizedNodeContext context = this.restconfImpl
                .invokeRpc("sal-remote:create-notification-stream", payload, null);
        assertNotNull(context);
    }

    /**
     * Tests stream entry node.
     */
    @Test
    public void toStreamEntryNodeTest() {
        final Module restconfModule = this.controllerContext.getRestconfModule();
        final DataSchemaNode streamSchemaNode = this.controllerContext
                .getRestconfModuleRestConfSchemaNode(restconfModule, Draft02.RestConfModule.STREAM_LIST_SCHEMA_NODE);
        final ListSchemaNode listStreamSchemaNode = (ListSchemaNode) streamSchemaNode;
        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamNodeValues =
                Builders.mapEntryBuilder(listStreamSchemaNode);
        List<DataSchemaNode> instanceDataChildrenByName =
                ControllerContext.findInstanceDataChildrenByName(listStreamSchemaNode, "name");
        final DataSchemaNode nameSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        streamNodeValues.withChild(Builders.leafBuilder((LeafSchemaNode) nameSchemaNode).withValue("").build());

        instanceDataChildrenByName =
                ControllerContext.findInstanceDataChildrenByName(listStreamSchemaNode, "description");
        final DataSchemaNode descriptionSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        streamNodeValues.withChild(
                Builders.leafBuilder((LeafSchemaNode) nameSchemaNode).withValue("DESCRIPTION_PLACEHOLDER").build());

        instanceDataChildrenByName =
                ControllerContext.findInstanceDataChildrenByName(listStreamSchemaNode, "replay-support");
        final DataSchemaNode replaySupportSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        streamNodeValues.withChild(
                Builders.leafBuilder((LeafSchemaNode) replaySupportSchemaNode).withValue(Boolean.TRUE).build());

        instanceDataChildrenByName =
                ControllerContext.findInstanceDataChildrenByName(listStreamSchemaNode, "replay-log-creation-time");
        final DataSchemaNode replayLogCreationTimeSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        streamNodeValues.withChild(
                Builders.leafBuilder((LeafSchemaNode) replayLogCreationTimeSchemaNode).withValue("").build());
        instanceDataChildrenByName = ControllerContext.findInstanceDataChildrenByName(listStreamSchemaNode, "events");
        final DataSchemaNode eventsSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        streamNodeValues.withChild(
                Builders.leafBuilder((LeafSchemaNode) eventsSchemaNode).withValue(Empty.getInstance()).build());
        assertNotNull(streamNodeValues.build());

    }

    /**
     * Subscribe for notification stream of toaster module.
     */
    @Test
    public void subscribeToNotificationStreamTest() throws Exception {
        final String identifier = "create-notification-stream/toaster:toastDone";

        // register test notification stream
        final SchemaPath path = SchemaPath.create(
                true, QName.create("http://netconfcentral.org/ns/toaster", "2009-11-20", "toastDone"));
        Notificator.createNotificationListener(Lists.newArrayList(path), identifier, "XML", controllerContext);

        final UriInfo uriInfo = mock(UriInfo.class);
        final UriBuilder uriBuilder = mock(UriBuilder.class);
        when(uriBuilder.port(8181)).thenReturn(uriBuilder);
        when(uriBuilder.replacePath(identifier)).thenReturn(uriBuilder);
        when(uriBuilder.build()).thenReturn(new URI(""));
        when(uriBuilder.scheme("ws")).thenReturn(uriBuilder);
        when(uriInfo.getAbsolutePathBuilder()).thenReturn(uriBuilder);
        final MultivaluedMap<String, String> map = mock(MultivaluedMap.class);
        final Set<Entry<String, List<String>>> set = new HashSet<>();
        when(map.entrySet()).thenReturn(set);
        when(uriInfo.getQueryParameters()).thenReturn(map);

        // subscribe to stream and verify response
        final NormalizedNodeContext response = this.restconfImpl.subscribeToStream(identifier, uriInfo);

        // remove test notification stream
        Notificator.removeAllListeners();
    }
}
