/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.schema.mapping;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.CREATE_SUBSCRIPTION_RPC_CONTENT;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.CREATE_SUBSCRIPTION_RPC_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.GET_SCHEMA_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_CANDIDATE_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_COMMIT_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_DISCARD_CHANGES_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_CONFIG_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_LOCK_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_RUNNING_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.createEditConfigStructure;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.toFilterStructure;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.toId;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.toPath;
import static org.opendaylight.netconf.util.NetconfUtil.NETCONF_DATA_QNAME;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.ArrayList;
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
import org.opendaylight.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.mdsal.dom.api.DOMActionResult;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.sal.connect.netconf.AbstractBaseSchemasTest;
import org.opendaylight.netconf.sal.connect.netconf.schema.NetconfRemoteSchemaYangSourceProvider;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.xpath.NetconfXPathContext;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.util.NetconfUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.IetfNetconfService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Capabilities;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.Schema;
import org.opendaylight.yangtools.rcf8528.data.util.EmptyMountPointContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
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
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public class NetconfMessageTransformerTest extends AbstractBaseSchemasTest {

    private static final String REVISION_EXAMPLE_SERVER_FARM = "2018-08-07";
    private static final String URN_EXAMPLE_SERVER_FARM = "urn:example:server-farm";

    private static final String REVISION_EXAMPLE_SERVER_FARM_2 = "2019-05-20";
    private static final String URN_EXAMPLE_SERVER_FARM_2 = "urn:example:server-farm-2";

    private static final String URN_EXAMPLE_CONFLICT = "urn:example:conflict";

    private static final String URN_EXAMPLE_AUGMENTED_ACTION = "urn:example:augmented-action";

    private static final String URN_EXAMPLE_RPCS_ACTIONS_OUTPUTS = "urn:example:rpcs-actions-outputs";

    private static final QName SERVER_QNAME =
            QName.create(URN_EXAMPLE_SERVER_FARM, REVISION_EXAMPLE_SERVER_FARM, "server");
    private static final QName RESET_QNAME = QName.create(SERVER_QNAME, "reset");
    private static final SchemaPath RESET_SERVER_PATH = SchemaPath.create(true, SERVER_QNAME, RESET_QNAME);
    private static final QName APPLICATIONS_QNAME = QName.create(URN_EXAMPLE_SERVER_FARM_2,
            REVISION_EXAMPLE_SERVER_FARM_2, "applications");
    private static final QName APPLICATION_QNAME = QName.create(APPLICATIONS_QNAME, "application");
    private static final QName KILL_QNAME = QName.create(APPLICATION_QNAME, "kill");
    private static final SchemaPath KILL_SERVER_APP_PATH =
            SchemaPath.create(true, SERVER_QNAME, APPLICATIONS_QNAME, APPLICATION_QNAME, KILL_QNAME);

    private static final QName DEVICE_QNAME =
            QName.create(URN_EXAMPLE_SERVER_FARM, REVISION_EXAMPLE_SERVER_FARM, "device");
    private static final QName START_QNAME = QName.create(DEVICE_QNAME, "start");
    private static final SchemaPath START_DEVICE_PATH = SchemaPath.create(true, DEVICE_QNAME, START_QNAME);
    private static final QName INTERFACE_QNAME = QName.create(DEVICE_QNAME, "interface");
    private static final QName ENABLE_QNAME = QName.create(INTERFACE_QNAME, "enable");
    private static final SchemaPath ENABLE_INTERFACE_PATH =
            SchemaPath.create(true, DEVICE_QNAME, INTERFACE_QNAME, ENABLE_QNAME);

    private static final QName DISABLE_QNAME = QName.create(URN_EXAMPLE_AUGMENTED_ACTION, "disable");
    private static final SchemaPath DISABLE_INTERFACE_PATH =
            SchemaPath.create(true, DEVICE_QNAME, INTERFACE_QNAME, DISABLE_QNAME);

    private static final QName CHECK_WITH_OUTPUT_QNAME =
            QName.create(URN_EXAMPLE_RPCS_ACTIONS_OUTPUTS, "check-with-output");
    private static final SchemaPath CHECK_WITH_OUTPUT_INTERFACE_PATH =
            SchemaPath.create(true, DEVICE_QNAME, INTERFACE_QNAME, CHECK_WITH_OUTPUT_QNAME);
    private static final QName CHECK_WITHOUT_OUTPUT_QNAME =
            QName.create(URN_EXAMPLE_RPCS_ACTIONS_OUTPUTS, "check-without-output");
    private static final SchemaPath CHECK_WITHOUT_OUTPUT_INTERFACE_PATH =
            SchemaPath.create(true, DEVICE_QNAME, INTERFACE_QNAME, CHECK_WITHOUT_OUTPUT_QNAME);
    private static final QName RPC_WITH_OUTPUT_QNAME =
            QName.create(URN_EXAMPLE_RPCS_ACTIONS_OUTPUTS, "rpc-with-output");
    private static final QName RPC_WITHOUT_OUTPUT_QNAME =
            QName.create(URN_EXAMPLE_RPCS_ACTIONS_OUTPUTS, "rpc-without-output");

    private static final QName BOX_OUT_QNAME =
            QName.create(URN_EXAMPLE_SERVER_FARM, REVISION_EXAMPLE_SERVER_FARM, "box-out");
    private static final QName BOX_IN_QNAME = QName.create(BOX_OUT_QNAME, "box-in");
    private static final QName OPEN_QNAME = QName.create(BOX_IN_QNAME, "open");
    private static final SchemaPath OPEN_BOXES_PATH =
            SchemaPath.create(true, BOX_OUT_QNAME, BOX_IN_QNAME, OPEN_QNAME);

    private static final QName FOO_QNAME = QName.create(URN_EXAMPLE_CONFLICT, "foo");
    private static final QName BAR_QNAME = QName.create(URN_EXAMPLE_CONFLICT, "bar");
    private static final QName XYZZY_QNAME = QName.create(URN_EXAMPLE_CONFLICT, "xyzzy");
    private static final SchemaPath XYZZY_FOO_PATH = SchemaPath.create(true, FOO_QNAME, XYZZY_QNAME);
    private static final SchemaPath XYZZY_BAR_PATH = SchemaPath.create(true, BAR_QNAME, XYZZY_QNAME);

    private static final QName CONFLICT_CHOICE_QNAME = QName.create(URN_EXAMPLE_CONFLICT, "conflict-choice");
    private static final QName CHOICE_CONT_QNAME = QName.create(URN_EXAMPLE_CONFLICT, "choice-cont");
    private static final QName CHOICE_ACTION_QNAME = QName.create(URN_EXAMPLE_CONFLICT, "choice-action");
    private static final SchemaPath CHOICE_ACTION_PATH =
            SchemaPath.create(true, CONFLICT_CHOICE_QNAME, CHOICE_CONT_QNAME, CHOICE_CONT_QNAME, CHOICE_ACTION_QNAME);

    private static EffectiveModelContext PARTIAL_SCHEMA;
    private static EffectiveModelContext SCHEMA;
    private static EffectiveModelContext ACTION_SCHEMA;

    private NetconfMessageTransformer actionNetconfMessageTransformer;
    private NetconfMessageTransformer netconfMessageTransformer;

    @BeforeClass
    public static void beforeClass() {
        PARTIAL_SCHEMA = BindingRuntimeHelpers.createEffectiveModel(NetconfState.class);
        SCHEMA = BindingRuntimeHelpers.createEffectiveModel(IetfNetconfService.class, NetconfState.class);
        ACTION_SCHEMA = YangParserTestUtils.parseYangResources(NetconfMessageTransformerTest.class,
            "/schemas/example-server-farm.yang","/schemas/example-server-farm-2.yang",
            "/schemas/conflicting-actions.yang", "/schemas/augmented-action.yang",
            "/schemas/rpcs-actions-outputs.yang");
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
        actionNetconfMessageTransformer = new NetconfMessageTransformer(new EmptyMountPointContext(ACTION_SCHEMA),
            true, BASE_SCHEMAS.getBaseSchema());
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
        final NetconfMessageTransformer transformer = new NetconfMessageTransformer(new EmptyMountPointContext(SCHEMA),
            true, BASE_SCHEMAS.getBaseSchemaWithNotifications());
        final NetconfMessage netconfMessage = transformer.toRpcRequest(
                toPath(CREATE_SUBSCRIPTION_RPC_QNAME),
                CREATE_SUBSCRIPTION_RPC_CONTENT
        );
        final String documentString = XmlUtil.toString(netconfMessage.getDocument());
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
    public void testRpcEmptyBodyWithOutputDefinedSchemaResult() throws Exception {
        final String result = "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"><ok/></rpc-reply>";

        final DOMRpcResult domRpcResult = actionNetconfMessageTransformer
                .toRpcResult(new NetconfMessage(XmlUtil.readXmlToDocument(result)),
                        toPath(RPC_WITH_OUTPUT_QNAME));
        assertNotNull(domRpcResult);
    }

    @Test
    public void testRpcEmptyBodyWithoutOutputDefinedSchemaResult() throws Exception {
        final String result = "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"><ok/></rpc-reply>";

        final DOMRpcResult domRpcResult = actionNetconfMessageTransformer
                .toRpcResult(new NetconfMessage(XmlUtil.readXmlToDocument(result)),
                        toPath(RPC_WITHOUT_OUTPUT_QNAME));
        assertNotNull(domRpcResult);
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
        final DOMSource schemaContent = ((DOMSourceAnyxmlNode) ((ContainerNode) compositeNodeRpcResult.getResult())
                .getValue().iterator().next()).getValue();
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

        final List<DataContainerChild<?, ?>> values = Lists.newArrayList(
                NetconfRemoteSchemaYangSourceProvider
                        .createGetSchemaRequest("module", Optional.of("2012-12-12")).getValue());

        final Map<QName, Object> keys = new HashMap<>();
        for (final DataContainerChild<?, ?> value : values) {
            keys.put(value.getNodeType(), value.getValue());
        }

        final NodeIdentifierWithPredicates identifierWithPredicates =
                NodeIdentifierWithPredicates.of(Schema.QNAME, keys);
        final MapEntryNode schemaNode =
                Builders.mapEntryBuilder().withNodeIdentifier(identifierWithPredicates).withValue(values).build();

        final DOMSourceAnyxmlNode data = (DOMSourceAnyxmlNode) ((ContainerNode) compositeNodeRpcResult.getResult())
                .getChild(toId(NETCONF_DATA_QNAME)).get();

        final NormalizedNodeResult nodeResult =
                NetconfUtil.transformDOMSourceToNormalizedNode(SCHEMA, data.getValue());
        final ContainerNode result = (ContainerNode) nodeResult.getResult();
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
                    NodeIdentifierWithPredicates.of(Schema.QNAME),
                    toId(QName.create(Schemas.QNAME, "version"))), SCHEMA);

        final DataContainerChild<?, ?> source = NetconfBaseOps.getSourceNode(NETCONF_RUNNING_QNAME);

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(toPath(NETCONF_GET_CONFIG_QNAME),
                NetconfMessageTransformUtil.wrap(NETCONF_GET_CONFIG_NODEID, source, filter));

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
                NetconfMessageTransformUtil.wrap(NETCONF_GET_CONFIG_NODEID, source, filter));

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
    public void testGetConfigRequestWithXPath() throws Exception {
        final String namespace = "urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring";
        final String xpath = "/urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring:netconf-state"
                + "/urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring:schemas";
        final NetconfXPathContext netconfXPathContext = new NetconfXPathContext(xpath);
        netconfXPathContext.addNamespace(namespace);

        final DataContainerChild<?, ?> filter = NetconfMessageTransformUtil.toFilterStructure(netconfXPathContext,
                true);

        final DataContainerChild<?, ?> source = NetconfBaseOps.getSourceNode(NETCONF_RUNNING_QNAME);

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(toPath(NETCONF_GET_CONFIG_QNAME),
                NetconfMessageTransformUtil.wrap(NETCONF_GET_CONFIG_NODEID, source, filter));

        assertSimilarXml(netconfMessage,
                "<rpc message-id=\"m-0\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                        + "<get-config>\n"
                        + "<source>\n"
                        + "<running/>\n"
                        + "</source>\n"
                        + "<filter"
                        + " xmlns:ns0=\"xmlns\""
                        + " ns0:nxpcrpc0=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\""
                        + " xmlns:ns1=\"urn:ietf:params:xml:ns:netconf:base:1.0\""
                        + " ns1:select=\"/nxpcrpc0:netconf-state/nxpcrpc0:schemas\""
                        + " xmlns:ns2=\"urn:ietf:params:xml:ns:netconf:base:1.0\""
                        + " ns2:type=\"xpath\"/>"
                        + "</get-config>"
                        + "</rpc>");
    }

    @Test
    public void testEditConfigRequest() throws Exception {
        final List<DataContainerChild<?, ?>> values = Lists.newArrayList(
                NetconfRemoteSchemaYangSourceProvider
                        .createGetSchemaRequest("module", Optional.of("2012-12-12")).getValue());

        final Map<QName, Object> keys = new HashMap<>();
        for (final DataContainerChild<?, ?> value : values) {
            keys.put(value.getNodeType(), value.getValue());
        }

        final NodeIdentifierWithPredicates identifierWithPredicates =
                NodeIdentifierWithPredicates.of(Schema.QNAME, keys);
        final MapEntryNode schemaNode =
                Builders.mapEntryBuilder().withNodeIdentifier(identifierWithPredicates).withValue(values).build();

        final YangInstanceIdentifier id = YangInstanceIdentifier.builder()
                .node(NetconfState.QNAME).node(Schemas.QNAME).node(Schema.QNAME)
                .nodeWithKey(Schema.QNAME, keys).build();
        final DataContainerChild<?, ?> editConfigStructure =
                createEditConfigStructure(BASE_SCHEMAS.getBaseSchemaWithNotifications().getEffectiveModelContext(), id,
                    Optional.empty(), Optional.ofNullable(schemaNode));

        final DataContainerChild<?, ?> target = NetconfBaseOps.getTargetNode(NETCONF_CANDIDATE_QNAME);

        final ContainerNode wrap =
                NetconfMessageTransformUtil.wrap(NETCONF_EDIT_CONFIG_NODEID, editConfigStructure, target);
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
                    new NodeWithValue<>(capability, "a:b:c")), SCHEMA);

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(toPath(NETCONF_GET_QNAME),
                NetconfMessageTransformUtil.wrap(NETCONF_GET_NODEID, filter));

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

    @Test
    public void testGetRequestWithXPath() throws Exception {
        final String namespace = "urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring";
        final String xpath = "/urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring:netconf-state"
                + "/urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring:capabilities"
                + "/urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring:capability[text()='a:b:c']";
        final NetconfXPathContext netconfXPathContext = new NetconfXPathContext(xpath);
        netconfXPathContext.addNamespace(namespace);

        final DataContainerChild<?, ?> filter = toFilterStructure(netconfXPathContext, true);

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(toPath(NETCONF_GET_QNAME),
                NetconfMessageTransformUtil.wrap(NETCONF_GET_NODEID, filter));

        assertSimilarXml(netconfMessage, "<rpc message-id=\"m-0\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                + "<get>\n"
                + "<filter"
                + " xmlns:ns0=\"xmlns\""
                + " ns0:nxpcrpc0=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\""
                + " xmlns:ns1=\"urn:ietf:params:xml:ns:netconf:base:1.0\""
                + " ns1:select=\"/nxpcrpc0:netconf-state/nxpcrpc0:capabilities/nxpcrpc0:capability[text()='a:b:c']\""
                + " xmlns:ns2=\"urn:ietf:params:xml:ns:netconf:base:1.0\""
                + " ns2:type=\"xpath\"/>\n"
                + "</get>\n"
                + "</rpc>");
    }

    private static NetconfMessageTransformer getTransformer(final EffectiveModelContext schema) {
        return new NetconfMessageTransformer(new EmptyMountPointContext(schema), true, BASE_SCHEMAS.getBaseSchema());
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
        final Set<SchemaPath> schemaPaths = new HashSet<>();
        schemaPaths.add(RESET_SERVER_PATH);
        schemaPaths.add(START_DEVICE_PATH);
        schemaPaths.add(ENABLE_INTERFACE_PATH);
        schemaPaths.add(OPEN_BOXES_PATH);
        schemaPaths.add(KILL_SERVER_APP_PATH);
        schemaPaths.add(XYZZY_FOO_PATH);
        schemaPaths.add(XYZZY_BAR_PATH);
        schemaPaths.add(CHOICE_ACTION_PATH);
        schemaPaths.add(DISABLE_INTERFACE_PATH);
        schemaPaths.add(CHECK_WITH_OUTPUT_INTERFACE_PATH);
        schemaPaths.add(CHECK_WITHOUT_OUTPUT_INTERFACE_PATH);

        final List<ActionDefinition> actions = NetconfMessageTransformer.getActions(ACTION_SCHEMA);
        assertEquals(schemaPaths.size(), actions.size());
        for (final ActionDefinition actionDefinition : actions) {
            final SchemaPath path = actionDefinition.getPath();
            assertTrue(schemaPaths.remove(path));
        }
    }

    @Test
    public void toActionRequestListTopLevelTest() {
        final QName nameQname = QName.create(SERVER_QNAME, "name");
        final List<PathArgument> nodeIdentifiers = new ArrayList<>();
        nodeIdentifiers.add(new NodeIdentifier(SERVER_QNAME));
        nodeIdentifiers.add(NodeIdentifierWithPredicates.of(SERVER_QNAME, nameQname, "test"));
        final DOMDataTreeIdentifier domDataTreeIdentifier = prepareDataTreeId(nodeIdentifiers);

        final ContainerNode data = initInputAction(QName.create(SERVER_QNAME, "reset-at"), "now");

        final NetconfMessage actionRequest = actionNetconfMessageTransformer.toActionRequest(
                RESET_SERVER_PATH, domDataTreeIdentifier, data);

        final Node childAction = checkBasePartOfActionRequest(actionRequest);

        final Node childServer = childAction.getFirstChild();
        checkNode(childServer, "server", "server", URN_EXAMPLE_SERVER_FARM);

        final Node childName = childServer.getFirstChild();
        checkNode(childName, "name", "name", URN_EXAMPLE_SERVER_FARM);

        final Node childTest = childName.getFirstChild();
        assertEquals(childTest.getNodeValue(), "test");

        checkAction(RESET_QNAME, childName.getNextSibling(), "reset-at", "reset-at", "now");
    }

    @Test
    public void toActionRequestContainerTopLevelTest() {
        final List<PathArgument> nodeIdentifiers = Collections.singletonList(NodeIdentifier.create(DEVICE_QNAME));
        final DOMDataTreeIdentifier domDataTreeIdentifier = prepareDataTreeId(nodeIdentifiers);

        final NormalizedNode<?, ?> payload = initInputAction(QName.create(DEVICE_QNAME, "start-at"), "now");
        final NetconfMessage actionRequest = actionNetconfMessageTransformer.toActionRequest(
                START_DEVICE_PATH, domDataTreeIdentifier, payload);

        final Node childAction = checkBasePartOfActionRequest(actionRequest);

        final Node childDevice = childAction.getFirstChild();
        checkNode(childDevice, "device", "device", URN_EXAMPLE_SERVER_FARM);

        checkAction(START_QNAME, childDevice.getFirstChild(), "start-at", "start-at", "now");
    }

    @Test
    public void toActionRequestContainerInContainerTest() {
        final List<PathArgument> nodeIdentifiers = new ArrayList<>();
        nodeIdentifiers.add(NodeIdentifier.create(BOX_OUT_QNAME));
        nodeIdentifiers.add(NodeIdentifier.create(BOX_IN_QNAME));

        final DOMDataTreeIdentifier domDataTreeIdentifier = prepareDataTreeId(nodeIdentifiers);

        final NormalizedNode<?, ?> payload = initInputAction(QName.create(BOX_OUT_QNAME, "start-at"), "now");
        final NetconfMessage actionRequest = actionNetconfMessageTransformer.toActionRequest(
                OPEN_BOXES_PATH, domDataTreeIdentifier, payload);

        final Node childAction = checkBasePartOfActionRequest(actionRequest);

        final Node childBoxOut = childAction.getFirstChild();
        checkNode(childBoxOut, "box-out", "box-out", URN_EXAMPLE_SERVER_FARM);

        final Node childBoxIn = childBoxOut.getFirstChild();
        checkNode(childBoxIn, "box-in", "box-in", URN_EXAMPLE_SERVER_FARM);

        final Node action = childBoxIn.getFirstChild();
        checkNode(action, OPEN_QNAME.getLocalName(), OPEN_QNAME.getLocalName(), OPEN_QNAME.getNamespace().toString());
    }

    @Test
    public void toActionRequestListInContainerTest() {
        final QName nameQname = QName.create(INTERFACE_QNAME, "name");

        final List<PathArgument> nodeIdentifiers = new ArrayList<>();
        nodeIdentifiers.add(NodeIdentifier.create(DEVICE_QNAME));
        nodeIdentifiers.add(NodeIdentifier.create(INTERFACE_QNAME));
        nodeIdentifiers.add(NodeIdentifierWithPredicates.of(INTERFACE_QNAME, nameQname, "test"));

        final DOMDataTreeIdentifier domDataTreeIdentifier = prepareDataTreeId(nodeIdentifiers);

        final NormalizedNode<?, ?> payload = initEmptyInputAction(INTERFACE_QNAME);
        final NetconfMessage actionRequest = actionNetconfMessageTransformer.toActionRequest(
                ENABLE_INTERFACE_PATH, domDataTreeIdentifier, payload);

        final Node childAction = checkBasePartOfActionRequest(actionRequest);

        final Node childDevice = childAction.getFirstChild();
        checkNode(childDevice, "device", "device", URN_EXAMPLE_SERVER_FARM);

        final Node childInterface = childDevice.getFirstChild();
        checkNode(childInterface, "interface", "interface", URN_EXAMPLE_SERVER_FARM);

        final Node childName = childInterface.getFirstChild();
        checkNode(childName, "name", "name", nameQname.getNamespace().toString());

        final Node childTest = childName.getFirstChild();
        assertEquals(childTest.getNodeValue(), "test");

        final Node action = childInterface.getLastChild();
        checkNode(action, ENABLE_QNAME.getLocalName(), ENABLE_QNAME.getLocalName(),
                ENABLE_QNAME.getNamespace().toString());
    }

    @Test
    public void toActionRequestListInContainerAugmentedIntoListTest() {
        final QName serverNameQname = QName.create(SERVER_QNAME, "name");
        final QName applicationNameQname = QName.create(APPLICATION_QNAME, "name");

        final List<PathArgument> nodeIdentifiers = new ArrayList<>();
        nodeIdentifiers.add(NodeIdentifier.create(SERVER_QNAME));
        nodeIdentifiers.add(NodeIdentifierWithPredicates.of(SERVER_QNAME, serverNameQname, "testServer"));
        nodeIdentifiers.add(new AugmentationIdentifier(Collections.singleton(APPLICATIONS_QNAME)));
        nodeIdentifiers.add(NodeIdentifier.create(APPLICATIONS_QNAME));
        nodeIdentifiers.add(NodeIdentifier.create(APPLICATION_QNAME));
        nodeIdentifiers.add(NodeIdentifierWithPredicates.of(APPLICATION_QNAME,
                applicationNameQname, "testApplication"));

        final DOMDataTreeIdentifier domDataTreeIdentifier = prepareDataTreeId(nodeIdentifiers);

        final NormalizedNode<?, ?> payload = initEmptyInputAction(APPLICATION_QNAME);
        final NetconfMessage actionRequest = actionNetconfMessageTransformer.toActionRequest(
                KILL_SERVER_APP_PATH, domDataTreeIdentifier, payload);

        final Node childAction = checkBasePartOfActionRequest(actionRequest);

        final Node childServer = childAction.getFirstChild();
        checkNode(childServer, "server", "server", URN_EXAMPLE_SERVER_FARM);

        final Node childServerName = childServer.getFirstChild();
        checkNode(childServerName, "name", "name", URN_EXAMPLE_SERVER_FARM);

        final Node childServerNameTest = childServerName.getFirstChild();
        assertEquals(childServerNameTest.getNodeValue(), "testServer");

        final Node childApplications = childServer.getLastChild();
        checkNode(childApplications, "applications", "applications", URN_EXAMPLE_SERVER_FARM_2);

        final Node childApplication = childApplications.getFirstChild();
        checkNode(childApplication, "application", "application", URN_EXAMPLE_SERVER_FARM_2);

        final Node childApplicationName = childApplication.getFirstChild();
        checkNode(childApplicationName, "name", "name", URN_EXAMPLE_SERVER_FARM_2);

        final Node childApplicationNameTest = childApplicationName.getFirstChild();
        assertEquals(childApplicationNameTest.getNodeValue(), "testApplication");

        final Node childKillAction = childApplication.getLastChild();
        checkNode(childApplication, "application", "application", URN_EXAMPLE_SERVER_FARM_2);
        checkNode(childKillAction, KILL_QNAME.getLocalName(), KILL_QNAME.getLocalName(),
                KILL_QNAME.getNamespace().toString());
    }

    @Test
    public void toActionRequestConflictingInListTest() {
        final QName barInputQname = QName.create(BAR_QNAME, "bar");
        final QName barIdQname = QName.create(BAR_QNAME, "bar-id");
        final Byte barInput = 1;

        final List<PathArgument> nodeIdentifiers = new ArrayList<>();
        nodeIdentifiers.add(NodeIdentifier.create(BAR_QNAME));
        nodeIdentifiers.add(NodeIdentifierWithPredicates.of(BAR_QNAME, barIdQname, "test"));

        final DOMDataTreeIdentifier domDataTreeIdentifier = prepareDataTreeId(nodeIdentifiers);

        final ImmutableLeafNodeBuilder<Byte> immutableLeafNodeBuilder = new ImmutableLeafNodeBuilder<>();
        final DataContainerChild<NodeIdentifier, Byte> build = immutableLeafNodeBuilder.withNodeIdentifier(
                NodeIdentifier.create(barInputQname)).withValue(barInput).build();
        final NormalizedNode<?, ?> payload = ImmutableContainerNodeBuilder.create()
                .withNodeIdentifier(NodeIdentifier.create(QName.create(barInputQname, "input")))
                .withChild(build).build();

        final NetconfMessage actionRequest = actionNetconfMessageTransformer.toActionRequest(
                XYZZY_BAR_PATH, domDataTreeIdentifier, payload);

        final Node childAction = checkBasePartOfActionRequest(actionRequest);

        final Node childBar = childAction.getFirstChild();
        checkNode(childBar, "bar", "bar", URN_EXAMPLE_CONFLICT);

        final Node childBarId = childBar.getFirstChild();
        checkNode(childBarId, "bar-id", "bar-id", URN_EXAMPLE_CONFLICT);

        final Node childTest = childBarId.getFirstChild();
        assertEquals(childTest.getNodeValue(), "test");

        final Node action = childBar.getLastChild();
        checkNode(action, XYZZY_QNAME.getLocalName(), XYZZY_QNAME.getLocalName(),
                XYZZY_QNAME.getNamespace().toString());
    }

    @Test
    public void toActionRequestConflictingInContainerTest() {
        final QName fooInputQname = QName.create(FOO_QNAME, "foo");

        final List<PathArgument> nodeIdentifiers = new ArrayList<>();
        nodeIdentifiers.add(NodeIdentifier.create(FOO_QNAME));
        final DOMDataTreeIdentifier domDataTreeIdentifier = prepareDataTreeId(nodeIdentifiers);
        final NormalizedNode<?, ?> payload = initInputAction(fooInputQname, "test");

        final NetconfMessage actionRequest = actionNetconfMessageTransformer.toActionRequest(
                XYZZY_FOO_PATH, domDataTreeIdentifier, payload);

        final Node childAction = checkBasePartOfActionRequest(actionRequest);

        final Node childBar = childAction.getFirstChild();
        checkNode(childBar, "foo", "foo", URN_EXAMPLE_CONFLICT);

        final Node action = childBar.getLastChild();
        checkNode(action, XYZZY_QNAME.getLocalName(), XYZZY_QNAME.getLocalName(),
                XYZZY_QNAME.getNamespace().toString());
    }

    @Test
    public void toActionRequestChoiceTest() {
        final List<PathArgument> nodeIdentifiers = new ArrayList<>();
        nodeIdentifiers.add(NodeIdentifier.create(CONFLICT_CHOICE_QNAME));
        nodeIdentifiers.add(NodeIdentifier.create(CHOICE_CONT_QNAME));
        final DOMDataTreeIdentifier domDataTreeIdentifier = prepareDataTreeId(nodeIdentifiers);
        final NormalizedNode<?, ?> payload = initEmptyInputAction(CHOICE_ACTION_QNAME);

        final NetconfMessage actionRequest = actionNetconfMessageTransformer.toActionRequest(
                CHOICE_ACTION_PATH, domDataTreeIdentifier, payload);

        final Node childAction = checkBasePartOfActionRequest(actionRequest);

        final Node childChoiceCont = childAction.getFirstChild();
        checkNode(childChoiceCont, "choice-cont", "choice-cont", URN_EXAMPLE_CONFLICT);

        final Node action = childChoiceCont.getLastChild();
        checkNode(action, CHOICE_ACTION_QNAME.getLocalName(), CHOICE_ACTION_QNAME.getLocalName(),
                CHOICE_ACTION_QNAME.getNamespace().toString());
    }

    @Test
    public void toAugmentedActionRequestListInContainerTest() {
        final QName nameQname = QName.create(INTERFACE_QNAME, "name");

        final List<PathArgument> nodeIdentifiers = new ArrayList<>();
        nodeIdentifiers.add(NodeIdentifier.create(DEVICE_QNAME));
        nodeIdentifiers.add(NodeIdentifier.create(INTERFACE_QNAME));
        nodeIdentifiers.add(NodeIdentifierWithPredicates.of(INTERFACE_QNAME, nameQname, "test"));

        final DOMDataTreeIdentifier domDataTreeIdentifier = prepareDataTreeId(nodeIdentifiers);

        final NormalizedNode<?, ?> payload = initEmptyInputAction(INTERFACE_QNAME);
        final NetconfMessage actionRequest = actionNetconfMessageTransformer.toActionRequest(
                DISABLE_INTERFACE_PATH, domDataTreeIdentifier, payload);

        final Node childAction = checkBasePartOfActionRequest(actionRequest);

        final Node childDevice = childAction.getFirstChild();
        checkNode(childDevice, "device", "device", URN_EXAMPLE_SERVER_FARM);

        final Node childInterface = childDevice.getFirstChild();
        checkNode(childInterface, "interface", "interface", URN_EXAMPLE_SERVER_FARM);

        final Node childName = childInterface.getFirstChild();
        checkNode(childName, "name", "name", nameQname.getNamespace().toString());

        final Node childTest = childName.getFirstChild();
        assertEquals(childTest.getNodeValue(), "test");

        final Node action = childInterface.getLastChild();
        checkNode(action, DISABLE_QNAME.getLocalName(), DISABLE_QNAME.getLocalName(),
                DISABLE_QNAME.getNamespace().toString());
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void toActionResultTest() throws Exception {
        final NetconfMessage message = new NetconfMessage(XmlUtil.readXmlToDocument(
                "<rpc-reply message-id=\"101\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">"
                + "<reset-finished-at xmlns=\"urn:example:server-farm\">"
                + "now"
                + "</reset-finished-at>"
                + "</rpc-reply>"));
        final DOMActionResult actionResult = actionNetconfMessageTransformer.toActionResult(RESET_SERVER_PATH, message);
        assertNotNull(actionResult);
        final ContainerNode containerNode = actionResult.getOutput().get();
        assertNotNull(containerNode);
        final LeafNode<String> leaf = (LeafNode) containerNode.getValue().iterator().next();
        assertEquals("now", leaf.getValue());
    }

    @Test
    public void toActionEmptyBodyWithOutputDefinedResultTest() throws Exception {
        final NetconfMessage message = new NetconfMessage(XmlUtil.readXmlToDocument(
                "<rpc-reply message-id=\"101\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">"
                + "<ok/>"
                + "</rpc-reply>"));
        final DOMActionResult actionResult =
                actionNetconfMessageTransformer.toActionResult(CHECK_WITH_OUTPUT_INTERFACE_PATH, message);
        assertNotNull(actionResult);
        assertTrue(actionResult.getOutput().isEmpty());
    }

    @Test
    public void toActionEmptyBodyWithoutOutputDefinedResultTest() throws Exception {
        final NetconfMessage message = new NetconfMessage(XmlUtil.readXmlToDocument(
                "<rpc-reply message-id=\"101\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">"
                + "<ok/>"
                + "</rpc-reply>"));
        final DOMActionResult actionResult =
                actionNetconfMessageTransformer.toActionResult(CHECK_WITHOUT_OUTPUT_INTERFACE_PATH, message);
        assertNotNull(actionResult);
        assertTrue(actionResult.getOutput().isEmpty());
    }

    private static void checkAction(final QName actionQname, final Node action , final String inputLocalName,
            final String inputNodeName, final String inputValue) {
        checkNode(action, actionQname.getLocalName(), actionQname.getLocalName(),
                actionQname.getNamespace().toString());

        final Node childResetAt = action.getFirstChild();
        checkNode(childResetAt, inputLocalName, inputNodeName, actionQname.getNamespace().toString());

        final Node firstChild = childResetAt.getFirstChild();
        assertEquals(firstChild.getNodeValue(), inputValue);
    }

    private static Node checkBasePartOfActionRequest(final NetconfMessage actionRequest) {
        final Node baseRpc = actionRequest.getDocument().getFirstChild();
        checkNode(baseRpc, "rpc", "rpc", NetconfUtil.NETCONF_QNAME.getNamespace().toString());
        assertTrue(baseRpc.getLocalName().equals("rpc"));
        assertTrue(baseRpc.getNodeName().equals("rpc"));

        final Node messageId = baseRpc.getAttributes().getNamedItem("message-id");
        assertNotNull(messageId);
        assertTrue(messageId.getNodeValue().contains("m-"));

        final Node childAction = baseRpc.getFirstChild();
        checkNode(childAction, "action", "action", NetconfMessageTransformUtil.NETCONF_ACTION_NAMESPACE.toString());
        return childAction;
    }

    private static DOMDataTreeIdentifier prepareDataTreeId(final List<PathArgument> nodeIdentifiers) {
        final YangInstanceIdentifier yangInstanceIdentifier =
                YangInstanceIdentifier.builder().append(nodeIdentifiers).build();
        final DOMDataTreeIdentifier domDataTreeIdentifier =
                new DOMDataTreeIdentifier(org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION,
                        yangInstanceIdentifier);
        return domDataTreeIdentifier;
    }

    private static ContainerNode initInputAction(final QName qname, final String value) {
        final ImmutableLeafNodeBuilder<String> immutableLeafNodeBuilder = new ImmutableLeafNodeBuilder<>();
        final DataContainerChild<NodeIdentifier, String> build = immutableLeafNodeBuilder.withNodeIdentifier(
                NodeIdentifier.create(qname)).withValue(value).build();
        final ContainerNode data = ImmutableContainerNodeBuilder.create().withNodeIdentifier(NodeIdentifier.create(
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
