/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.schema.mapping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.CREATE_SUBSCRIPTION_RPC_CONTENT;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.CREATE_SUBSCRIPTION_RPC_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.GET_SCHEMA_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_CANDIDATE_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_COMMIT_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_DISCARD_CHANGES_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_LOCK_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_RUNNING_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.createEditConfigStructure;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.toFilterStructure;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.toId;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.toPath;
import static org.opendaylight.netconf.util.NetconfUtil.NETCONF_DATA_QNAME;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.xml.transform.dom.DOMSource;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.ElementNameAndAttributeQualifier;
import org.custommonkey.xmlunit.XMLUnit;
import org.hamcrest.CoreMatchers;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.binding.generator.impl.ModuleInfoBackedContext;
import org.opendaylight.mdsal.dom.api.DOMActionResult;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.sal.connect.netconf.schema.NetconfRemoteSchemaYangSourceProvider;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.util.NetconfUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.$YangModuleInfoImpl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Capabilities;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.Schema;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.AnyXmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableLeafNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public class NetconfMessageTransformerTest {

    private static final String REVISION_EXAMPLE_SERVER_FARM = "2018-08-07";
    private static final String URN_EXAMPLE_SERVER_FARM = "urn:example:server-farm";

    private static final String REVISION_EXAMPLE_SERVER_FARM_2 = "2019-05-20";
    private static final String URN_EXAMPLE_SERVER_FARM_2 = "urn:example:server-farm-2";

    private static SchemaContext PARTIAL_SCHEMA;
    private static SchemaContext SCHEMA;
    private static SchemaContext ACTION_SCHEMA;

    private NetconfMessageTransformer actionNetconfMessageTransformer;
    private NetconfMessageTransformer netconfMessageTransformer;

    @BeforeClass
    public static void beforeClass() {
        final ModuleInfoBackedContext context = ModuleInfoBackedContext.create();
        context.addModuleInfos(Collections.singleton(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf
            .netconf.monitoring.rev101004.$YangModuleInfoImpl.getInstance()));
        PARTIAL_SCHEMA = context.tryToCreateSchemaContext().get();

        context.addModuleInfos(Collections.singleton($YangModuleInfoImpl.getInstance()));
        SCHEMA = context.tryToCreateSchemaContext().get();

        ACTION_SCHEMA = YangParserTestUtils.parseYangResources(NetconfMessageTransformerTest.class,
            "/schemas/example-server-farm.yang","/schemas/example-server-farm-2.yang");
    }

    @AfterClass
    public static void afterClass() {
        PARTIAL_SCHEMA = null;
        SCHEMA = null;
        ACTION_SCHEMA = null;
    }

    @Before
    public void setUp() throws Exception {
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setIgnoreAttributeOrder(true);
        XMLUnit.setIgnoreComments(true);

        netconfMessageTransformer = getTransformer(SCHEMA);
        actionNetconfMessageTransformer = new NetconfMessageTransformer(ACTION_SCHEMA, true);
    }

    @Test
    public void testLockRequestBaseSchemaNotPresent() throws Exception {
        final NetconfMessageTransformer transformer = getTransformer(PARTIAL_SCHEMA);
        final NetconfMessage netconfMessage = transformer.toRpcRequest(toPath(NETCONF_LOCK_QNAME),
                NetconfBaseOps.getLockContent(NETCONF_CANDIDATE_QNAME));

        assertThat(XmlUtil.toString(netconfMessage.getDocument()), CoreMatchers.containsString("<lock"));
        assertThat(XmlUtil.toString(netconfMessage.getDocument()), CoreMatchers.containsString("<rpc"));
    }

    @Test
    public void testCreateSubscriberNotificationSchemaNotPresent() throws Exception {
        final NetconfMessageTransformer transformer = new NetconfMessageTransformer(SCHEMA, true,
                BaseSchema.BASE_NETCONF_CTX_WITH_NOTIFICATIONS);
        NetconfMessage netconfMessage = transformer.toRpcRequest(
                toPath(CREATE_SUBSCRIPTION_RPC_QNAME),
                CREATE_SUBSCRIPTION_RPC_CONTENT
        );
        String documentString = XmlUtil.toString(netconfMessage.getDocument());
        assertThat(documentString, CoreMatchers.containsString("<create-subscription"));
        assertThat(documentString, CoreMatchers.containsString("<rpc"));
    }

    @Test
    public void tesLockSchemaRequest() throws Exception {
        final NetconfMessageTransformer transformer = getTransformer(PARTIAL_SCHEMA);
        final String result = "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"><ok/></rpc-reply>";

        transformer.toRpcResult(new NetconfMessage(XmlUtil.readXmlToDocument(result)), toPath(NETCONF_LOCK_QNAME));
    }

    @Test
    public void testDiscardChangesRequest() throws Exception {
        final NetconfMessage netconfMessage =
                netconfMessageTransformer.toRpcRequest(toPath(NETCONF_DISCARD_CHANGES_QNAME), null);
        assertThat(XmlUtil.toString(netconfMessage.getDocument()), CoreMatchers.containsString("<discard"));
        assertThat(XmlUtil.toString(netconfMessage.getDocument()), CoreMatchers.containsString("<rpc"));
        assertThat(XmlUtil.toString(netconfMessage.getDocument()), CoreMatchers.containsString("message-id"));
    }

    @Test
    public void testGetSchemaRequest() throws Exception {
        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(toPath(GET_SCHEMA_QNAME),
                NetconfRemoteSchemaYangSourceProvider.createGetSchemaRequest("module", Optional.of("2012-12-12")));
        assertSimilarXml(netconfMessage, "<rpc message-id=\"m-0\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                + "<get-schema xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\">\n"
                + "<format>yang</format>\n"
                + "<identifier>module</identifier>\n"
                + "<version>2012-12-12</version>\n"
                + "</get-schema>\n"
                + "</rpc>");
    }

    @Test
    public void testGetSchemaResponse() throws Exception {
        final NetconfMessageTransformer transformer = getTransformer(SCHEMA);
        final NetconfMessage response = new NetconfMessage(XmlUtil.readXmlToDocument(
                "<rpc-reply message-id=\"101\"\n"
                        + "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                        + "<data\n"
                        + "xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\">\n"
                        + "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n"
                        + "Random YANG SCHEMA\n"
                        + "</xs:schema>\n"
                        + "</data>\n"
                        + "</rpc-reply>"
        ));
        final DOMRpcResult compositeNodeRpcResult = transformer.toRpcResult(response, toPath(GET_SCHEMA_QNAME));
        assertTrue(compositeNodeRpcResult.getErrors().isEmpty());
        assertNotNull(compositeNodeRpcResult.getResult());
        final DOMSource schemaContent =
            ((AnyXmlNode) ((ContainerNode) compositeNodeRpcResult.getResult()).getValue().iterator().next()).getValue();
        assertThat(schemaContent.getNode().getTextContent(),
                CoreMatchers.containsString("Random YANG SCHEMA"));
    }

    @Test
    public void testGetConfigResponse() throws Exception {
        final NetconfMessage response = new NetconfMessage(XmlUtil.readXmlToDocument("<rpc-reply message-id=\"101\"\n"
                + "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                + "<data>\n"
                + "<netconf-state xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\">\n"
                + "<schemas>\n"
                + "<schema>\n"
                + "<identifier>module</identifier>\n"
                + "<version>2012-12-12</version>\n"
                + "<format xmlns:x=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\">x:yang</format>\n"
                + "</schema>\n"
                + "</schemas>\n"
                + "</netconf-state>\n"
                + "</data>\n"
                + "</rpc-reply>"));

        final NetconfMessageTransformer transformer = getTransformer(SCHEMA);
        final DOMRpcResult compositeNodeRpcResult =
                transformer.toRpcResult(response, toPath(NETCONF_GET_CONFIG_QNAME));
        assertTrue(compositeNodeRpcResult.getErrors().isEmpty());
        assertNotNull(compositeNodeRpcResult.getResult());

        final List<DataContainerChild<? extends YangInstanceIdentifier.PathArgument, ?>> values = Lists.newArrayList(
                NetconfRemoteSchemaYangSourceProvider
                        .createGetSchemaRequest("module", Optional.of("2012-12-12")).getValue());

        final Map<QName, Object> keys = new HashMap<>();
        for (final DataContainerChild<? extends YangInstanceIdentifier.PathArgument, ?> value : values) {
            keys.put(value.getNodeType(), value.getValue());
        }

        final YangInstanceIdentifier.NodeIdentifierWithPredicates identifierWithPredicates =
                new YangInstanceIdentifier.NodeIdentifierWithPredicates(Schema.QNAME, keys);
        final MapEntryNode schemaNode =
                Builders.mapEntryBuilder().withNodeIdentifier(identifierWithPredicates).withValue(values).build();

        final AnyXmlNode data = (AnyXmlNode) ((ContainerNode) compositeNodeRpcResult
                .getResult()).getChild(toId(NETCONF_DATA_QNAME)).get();

        NormalizedNodeResult nodeResult =
                NetconfUtil.transformDOMSourceToNormalizedNode(SCHEMA, data.getValue());
        ContainerNode result = (ContainerNode) nodeResult.getResult();
        final ContainerNode state = (ContainerNode) result.getChild(toId(NetconfState.QNAME)).get();
        final ContainerNode schemas = (ContainerNode) state.getChild(toId(Schemas.QNAME)).get();
        final MapNode schemaParent = (MapNode) schemas.getChild(toId(Schema.QNAME)).get();
        assertEquals(1, Iterables.size(schemaParent.getValue()));

        assertEquals(schemaNode, schemaParent.getValue().iterator().next());
    }

    @Test
    public void testGetConfigLeafRequest() throws Exception {
        final DataContainerChild<?, ?> filter = toFilterStructure(
                YangInstanceIdentifier.create(toId(NetconfState.QNAME), toId(Schemas.QNAME), toId(Schema.QNAME),
                    new NodeIdentifierWithPredicates(Schema.QNAME, ImmutableMap.of()),
                    toId(QName.create(Schemas.QNAME, "version"))), SCHEMA);

        final DataContainerChild<?, ?> source = NetconfBaseOps.getSourceNode(NETCONF_RUNNING_QNAME);

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(toPath(NETCONF_GET_CONFIG_QNAME),
                NetconfMessageTransformUtil.wrap(NETCONF_GET_CONFIG_QNAME, source, filter));

        assertSimilarXml(netconfMessage, "<rpc message-id=\"m-0\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                + "<get-config xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                + "<filter xmlns:ns0=\"urn:ietf:params:xml:ns:netconf:base:1.0\" ns0:type=\"subtree\">\n"
                + "<netconf-state xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\">\n"
                + "<schemas>\n"
                + "<schema>\n"
                + "<version/>\n"
                + "</schema>\n"
                + "</schemas>\n"
                + "</netconf-state>\n"
                + "</filter>\n"
                + "<source>\n"
                + "<running/>\n"
                + "</source>\n"
                + "</get-config>\n"
                + "</rpc>");
    }

    @Test
    public void testGetConfigRequest() throws Exception {
        final DataContainerChild<?, ?> filter = toFilterStructure(
                YangInstanceIdentifier.create(toId(NetconfState.QNAME), toId(Schemas.QNAME)), SCHEMA);

        final DataContainerChild<?, ?> source = NetconfBaseOps.getSourceNode(NETCONF_RUNNING_QNAME);

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(toPath(NETCONF_GET_CONFIG_QNAME),
                NetconfMessageTransformUtil.wrap(NETCONF_GET_CONFIG_QNAME, source, filter));

        assertSimilarXml(netconfMessage, "<rpc message-id=\"m-0\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                + "<get-config xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                + "<filter xmlns:ns0=\"urn:ietf:params:xml:ns:netconf:base:1.0\" ns0:type=\"subtree\">\n"
                + "<netconf-state xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\">\n"
                + "<schemas/>\n"
                + "</netconf-state>"
                + "</filter>\n"
                + "<source>\n"
                + "<running/>\n"
                + "</source>\n"
                + "</get-config>"
                + "</rpc>");
    }

    @Test
    public void testEditConfigRequest() throws Exception {
        final List<DataContainerChild<? extends YangInstanceIdentifier.PathArgument, ?>> values = Lists.newArrayList(
                NetconfRemoteSchemaYangSourceProvider
                        .createGetSchemaRequest("module", Optional.of("2012-12-12")).getValue());

        final Map<QName, Object> keys = new HashMap<>();
        for (final DataContainerChild<? extends YangInstanceIdentifier.PathArgument, ?> value : values) {
            keys.put(value.getNodeType(), value.getValue());
        }

        final YangInstanceIdentifier.NodeIdentifierWithPredicates identifierWithPredicates =
                new YangInstanceIdentifier.NodeIdentifierWithPredicates(Schema.QNAME, keys);
        final MapEntryNode schemaNode =
                Builders.mapEntryBuilder().withNodeIdentifier(identifierWithPredicates).withValue(values).build();

        final YangInstanceIdentifier id = YangInstanceIdentifier.builder()
                .node(NetconfState.QNAME).node(Schemas.QNAME).node(Schema.QNAME)
                .nodeWithKey(Schema.QNAME, keys).build();
        final DataContainerChild<?, ?> editConfigStructure =
                createEditConfigStructure(BaseSchema.BASE_NETCONF_CTX_WITH_NOTIFICATIONS.getSchemaContext(), id,
                    Optional.empty(), Optional.ofNullable(schemaNode));

        final DataContainerChild<?, ?> target = NetconfBaseOps.getTargetNode(NETCONF_CANDIDATE_QNAME);

        final ContainerNode wrap =
                NetconfMessageTransformUtil.wrap(NETCONF_EDIT_CONFIG_QNAME, editConfigStructure, target);
        final NetconfMessage netconfMessage =
                netconfMessageTransformer.toRpcRequest(toPath(NETCONF_EDIT_CONFIG_QNAME), wrap);

        assertSimilarXml(netconfMessage, "<rpc message-id=\"m-0\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                + "<edit-config>\n"
                + "<target>\n"
                + "<candidate/>\n"
                + "</target>\n"
                + "<config>\n"
                + "<netconf-state xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\">\n"
                + "<schemas>\n"
                + "<schema>\n"
                + "<identifier>module</identifier>\n"
                + "<version>2012-12-12</version>\n"
                + "<format>yang</format>\n"
                + "</schema>\n"
                + "</schemas>\n"
                + "</netconf-state>\n"
                + "</config>\n"
                + "</edit-config>\n"
                + "</rpc>");
    }

    private static void assertSimilarXml(final NetconfMessage netconfMessage, final String xmlContent)
            throws SAXException, IOException {
        final Diff diff = XMLUnit.compareXML(netconfMessage.getDocument(), XmlUtil.readXmlToDocument(xmlContent));
        diff.overrideElementQualifier(new ElementNameAndAttributeQualifier());
        assertTrue(diff.toString(), diff.similar());
    }

    @Test
    public void testGetRequest() throws Exception {

        final QName capability = QName.create(Capabilities.QNAME, "capability");
        final DataContainerChild<?, ?> filter = toFilterStructure(
                YangInstanceIdentifier.create(toId(NetconfState.QNAME), toId(Capabilities.QNAME), toId(capability),
                    new YangInstanceIdentifier.NodeWithValue<>(capability, "a:b:c")), SCHEMA);

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(toPath(NETCONF_GET_QNAME),
                NetconfMessageTransformUtil.wrap(NETCONF_GET_QNAME, filter));

        assertSimilarXml(netconfMessage, "<rpc message-id=\"m-0\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">"
                + "<get xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                + "<filter xmlns:ns0=\"urn:ietf:params:xml:ns:netconf:base:1.0\" ns0:type=\"subtree\">\n"
                + "<netconf-state xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\">\n"
                + "<capabilities>\n"
                + "<capability>a:b:c</capability>\n"
                + "</capabilities>\n"
                + "</netconf-state>"
                + "</filter>\n"
                + "</get>"
                + "</rpc>");
    }

    private static NetconfMessageTransformer getTransformer(final SchemaContext schema) {
        return new NetconfMessageTransformer(schema, true);
    }

    @Test
    public void testCommitResponse() throws Exception {
        final NetconfMessage response = new NetconfMessage(XmlUtil.readXmlToDocument(
                "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"><ok/></rpc-reply>"
        ));
        final DOMRpcResult compositeNodeRpcResult =
                netconfMessageTransformer.toRpcResult(response, toPath(NETCONF_COMMIT_QNAME));
        assertTrue(compositeNodeRpcResult.getErrors().isEmpty());
        assertNull(compositeNodeRpcResult.getResult());
    }

    @Test
    public void getActionsTest() {
        QName reset = QName.create(URN_EXAMPLE_SERVER_FARM, REVISION_EXAMPLE_SERVER_FARM, "reset");
        QName start = QName.create(reset, "start");
        QName open = QName.create(start, "open");
        QName enable = QName.create(open, "enable");
        QName kill = QName.create(URN_EXAMPLE_SERVER_FARM_2, REVISION_EXAMPLE_SERVER_FARM_2, "kill");
        Set<QName> qnames = new HashSet<>(Arrays.asList(reset, start, open, enable, kill));
        Set<ActionDefinition> actions = actionNetconfMessageTransformer.getActions();
        assertTrue(!actions.isEmpty());
        for (ActionDefinition actionDefinition : actions) {
            QName qname = actionDefinition.getQName();
            assertTrue(qnames.contains(qname));
            qnames.remove(qname);
        }
    }

    @Test
    public void toActionRequestListTopLevelTest() {
        QName qname = QName.create(URN_EXAMPLE_SERVER_FARM, REVISION_EXAMPLE_SERVER_FARM, "server");
        QName nameQname = QName.create(qname, "name");
        QName actionResetQName = QName.create(qname, "reset");
        List<PathArgument> nodeIdentifiers = new ArrayList<>();
        nodeIdentifiers.add(new NodeIdentifier(qname));
        nodeIdentifiers.add(new NodeIdentifierWithPredicates(qname, nameQname, "test"));
        DOMDataTreeIdentifier domDataTreeIdentifier = prepareDataTreeId(nodeIdentifiers);

        ContainerNode data = initInputAction(QName.create(qname, "reset-at"), "now");

        NetconfMessage actionRequest = actionNetconfMessageTransformer.toActionRequest(
                SchemaPath.create(true, actionResetQName), domDataTreeIdentifier, data);

        Node childAction = checkBasePartOfActionRequest(actionRequest);

        Node childServer = childAction.getFirstChild();
        checkNode(childServer, "server", "server", qname.getNamespace().toString());

        Node childName = childServer.getFirstChild();
        checkNode(childName, "name", "name", qname.getNamespace().toString());

        Node childTest = childName.getFirstChild();
        assertEquals(childTest.getNodeValue(), "test");

        checkAction(actionResetQName, childName.getNextSibling(), "reset-at", "reset-at", "now");
    }

    @Test
    public void toActionRequestContainerTopLevelTest() {
        QName qname = QName.create(URN_EXAMPLE_SERVER_FARM, REVISION_EXAMPLE_SERVER_FARM, "device");
        QName actionStartQName = QName.create(qname, "start");

        List<PathArgument> nodeIdentifiers = Collections.singletonList(NodeIdentifier.create(qname));
        DOMDataTreeIdentifier domDataTreeIdentifier = prepareDataTreeId(nodeIdentifiers);

        NormalizedNode<?, ?> payload = initInputAction(QName.create(qname, "start-at"), "now");
        NetconfMessage actionRequest = actionNetconfMessageTransformer.toActionRequest(
                SchemaPath.create(true, actionStartQName), domDataTreeIdentifier, payload);

        Node childAction = checkBasePartOfActionRequest(actionRequest);

        Node childDevice = childAction.getFirstChild();
        checkNode(childDevice, "device", "device", qname.getNamespace().toString());

        checkAction(actionStartQName, childDevice.getFirstChild(), "start-at", "start-at", "now");
    }

    @Test
    public void toActionRequestContainerInContainerTest() {
        QName boxOutQName = QName.create(URN_EXAMPLE_SERVER_FARM, REVISION_EXAMPLE_SERVER_FARM, "box-out");
        QName boxInQName = QName.create(URN_EXAMPLE_SERVER_FARM, REVISION_EXAMPLE_SERVER_FARM, "box-in");
        QName actionOpenQName = QName.create(boxOutQName, "open");

        List<PathArgument> nodeIdentifiers = new ArrayList<>();
        nodeIdentifiers.add(NodeIdentifier.create(boxOutQName));
        nodeIdentifiers.add(NodeIdentifier.create(boxInQName));

        DOMDataTreeIdentifier domDataTreeIdentifier = prepareDataTreeId(nodeIdentifiers);

        NormalizedNode<?, ?> payload = initInputAction(QName.create(boxOutQName, "start-at"), "now");
        NetconfMessage actionRequest = actionNetconfMessageTransformer.toActionRequest(
                SchemaPath.create(true, actionOpenQName), domDataTreeIdentifier, payload);

        Node childAction = checkBasePartOfActionRequest(actionRequest);

        Node childBoxOut = childAction.getFirstChild();
        checkNode(childBoxOut, "box-out", "box-out", boxOutQName.getNamespace().toString());

        Node childBoxIn = childBoxOut.getFirstChild();
        checkNode(childBoxIn, "box-in", "box-in", boxOutQName.getNamespace().toString());

        Node action = childBoxIn.getFirstChild();
        checkNode(action, null, actionOpenQName.getLocalName(), null);
    }

    @Test
    public void toActionRequestListInContainerTest() {
        QName qnameDevice = QName.create(URN_EXAMPLE_SERVER_FARM, REVISION_EXAMPLE_SERVER_FARM, "device");
        QName qnameInterface = QName.create(URN_EXAMPLE_SERVER_FARM, REVISION_EXAMPLE_SERVER_FARM, "interface");
        QName nameQname = QName.create(qnameInterface, "name");
        QName actionEnableQName = QName.create(qnameInterface, "enable");

        List<PathArgument> nodeIdentifiers = new ArrayList<>();
        nodeIdentifiers.add(NodeIdentifier.create(qnameDevice));
        nodeIdentifiers.add(NodeIdentifier.create(qnameInterface));
        nodeIdentifiers.add(new NodeIdentifierWithPredicates(qnameInterface, nameQname, "test"));

        DOMDataTreeIdentifier domDataTreeIdentifier = prepareDataTreeId(nodeIdentifiers);

        NormalizedNode<?, ?> payload = initEmptyInputAction(qnameInterface);
        NetconfMessage actionRequest = actionNetconfMessageTransformer.toActionRequest(
                SchemaPath.create(true, actionEnableQName), domDataTreeIdentifier, payload);

        Node childAction = checkBasePartOfActionRequest(actionRequest);

        Node childDevice = childAction.getFirstChild();
        checkNode(childDevice, "device", "device", qnameDevice.getNamespace().toString());

        Node childInterface = childDevice.getFirstChild();
        checkNode(childInterface, "interface", "interface", qnameInterface.getNamespace().toString());

        Node childName = childInterface.getFirstChild();
        checkNode(childName, "name", "name", nameQname.getNamespace().toString());

        Node childTest = childName.getFirstChild();
        assertEquals(childTest.getNodeValue(), "test");

        Node action = childInterface.getLastChild();
        checkNode(action, null, actionEnableQName.getLocalName(), null);
    }

    @Test
    public void toActionRequestListInContainerAugmentedIntoListTest() {
        QName qnameServer = QName.create(URN_EXAMPLE_SERVER_FARM, REVISION_EXAMPLE_SERVER_FARM, "server");
        QName serverNameQname = QName.create(qnameServer, "name");
        QName qnameAppliactions = QName.create(URN_EXAMPLE_SERVER_FARM_2,
                REVISION_EXAMPLE_SERVER_FARM_2, "applications");
        QName qnameAppliaction = QName.create(URN_EXAMPLE_SERVER_FARM_2,
                REVISION_EXAMPLE_SERVER_FARM_2, "application");
        QName applicationNameQname = QName.create(qnameAppliaction, "name");
        QName actionKillQName = QName.create(qnameAppliaction, "kill");

        List<PathArgument> nodeIdentifiers = new ArrayList<>();
        nodeIdentifiers.add(NodeIdentifier.create(qnameServer));
        nodeIdentifiers.add(new NodeIdentifierWithPredicates(qnameServer, serverNameQname, "testServer"));
        nodeIdentifiers.add(new YangInstanceIdentifier
                .AugmentationIdentifier(Collections.singleton(qnameAppliactions)));
        nodeIdentifiers.add(NodeIdentifier.create(qnameAppliactions));
        nodeIdentifiers.add(NodeIdentifier.create(qnameAppliaction));
        nodeIdentifiers.add(new NodeIdentifierWithPredicates(qnameAppliaction,
                applicationNameQname, "testApplication"));

        DOMDataTreeIdentifier domDataTreeIdentifier = prepareDataTreeId(nodeIdentifiers);

        NormalizedNode<?, ?> payload = initEmptyInputAction(qnameAppliaction);
        NetconfMessage actionRequest = actionNetconfMessageTransformer.toActionRequest(
                SchemaPath.create(true, actionKillQName), domDataTreeIdentifier, payload);

        Node childAction = checkBasePartOfActionRequest(actionRequest);

        Node childServer = childAction.getFirstChild();
        checkNode(childServer, "server", "server", qnameServer.getNamespace().toString());

        Node childServerName = childServer.getFirstChild();
        checkNode(childServerName, "name", "name", serverNameQname.getNamespace().toString());

        Node childServerNameTest = childServerName.getFirstChild();
        assertEquals(childServerNameTest.getNodeValue(), "testServer");

        Node childApplications = childServer.getLastChild();
        checkNode(childApplications, "applications", "applications", qnameAppliactions.getNamespace().toString());

        Node childApplication = childApplications.getFirstChild();
        checkNode(childApplication, "application", "application", qnameAppliaction.getNamespace().toString());

        Node childApplicationName = childApplication.getFirstChild();
        checkNode(childApplicationName, "name", "name", applicationNameQname.getNamespace().toString());

        Node childApplicationNameTest = childApplicationName.getFirstChild();
        assertEquals(childApplicationNameTest.getNodeValue(), "testApplication");

        Node childKillAction = childApplication.getLastChild();
        checkNode(childApplication, "application", "application", qnameAppliaction.getNamespace().toString());
        checkNode(childKillAction, null, actionKillQName.getLocalName(), null);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void toActionResultTest() throws Exception {
        QName qname = QName.create(URN_EXAMPLE_SERVER_FARM, REVISION_EXAMPLE_SERVER_FARM, "reset");

        NetconfMessage message = new NetconfMessage(XmlUtil.readXmlToDocument(
                "<rpc-reply message-id=\"101\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">"
                + "<reset-finished-at xmlns=\"urn:example:server-farm\">"
                + "now"
                + "</reset-finished-at>"
                + "</rpc-reply>"));
        DOMActionResult actionResult = actionNetconfMessageTransformer.toActionResult(
                SchemaPath.create(true, qname), message);
        assertNotNull(actionResult);
        ContainerNode containerNode = actionResult.getOutput().get();
        assertNotNull(containerNode);
        LeafNode<String> leaf = (LeafNode) containerNode.getValue().iterator().next();
        assertEquals("now", leaf.getValue());
    }

    private static void checkAction(final QName actionQname, final Node action , final String inputLocalName,
            final String inputNodeName, final String inputValue) {
        checkNode(action, null, actionQname.getLocalName(), null);

        Node childResetAt = action.getFirstChild();
        checkNode(childResetAt, inputLocalName, inputNodeName, actionQname.getNamespace().toString());

        Node firstChild = childResetAt.getFirstChild();
        assertEquals(firstChild.getNodeValue(), inputValue);
    }

    private static Node checkBasePartOfActionRequest(final NetconfMessage actionRequest) {
        Node baseRpc = actionRequest.getDocument().getFirstChild();
        checkNode(baseRpc, "rpc", "rpc", NetconfUtil.NETCONF_QNAME.getNamespace().toString());
        assertTrue(baseRpc.getLocalName().equals("rpc"));
        assertTrue(baseRpc.getNodeName().equals("rpc"));

        Node messageId = baseRpc.getAttributes().getNamedItem("message-id");
        assertNotNull(messageId);
        assertTrue(messageId.getNodeValue().contains("m-"));

        Node childAction = baseRpc.getFirstChild();
        checkNode(childAction, "action", "action", NetconfMessageTransformUtil.NETCONF_ACTION_NAMESPACE.toString());
        return childAction;
    }

    private static DOMDataTreeIdentifier prepareDataTreeId(final List<PathArgument> nodeIdentifiers) {
        YangInstanceIdentifier yangInstanceIdentifier =
                YangInstanceIdentifier.builder().append(nodeIdentifiers).build();
        DOMDataTreeIdentifier domDataTreeIdentifier =
                new DOMDataTreeIdentifier(org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION,
                        yangInstanceIdentifier);
        return domDataTreeIdentifier;
    }

    private static ContainerNode initInputAction(final QName qname, final String value) {
        ImmutableLeafNodeBuilder<String> immutableLeafNodeBuilder = new ImmutableLeafNodeBuilder<>();
        DataContainerChild<NodeIdentifier, String> build = immutableLeafNodeBuilder.withNodeIdentifier(
                NodeIdentifier.create(qname)).withValue(value).build();
        ContainerNode data = ImmutableContainerNodeBuilder.create().withNodeIdentifier(NodeIdentifier.create(
                QName.create(qname, "input"))).withChild(build).build();
        return data;
    }

    private static ContainerNode initEmptyInputAction(final QName qname) {
        return ImmutableContainerNodeBuilder.create().withNodeIdentifier(NodeIdentifier.create(
                QName.create(qname, "input"))).build();
    }

    private static void checkNode(final Node childServer, final String expectedLocalName, final String expectedNodeName,
            final String expectedNamespace) {
        assertNotNull(childServer);
        assertEquals(childServer.getLocalName(), expectedLocalName);
        assertEquals(childServer.getNodeName(), expectedNodeName);
        assertEquals(childServer.getNamespaceURI(), expectedNamespace);
    }
}
