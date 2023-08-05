/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.CREATE_SUBSCRIPTION_RPC_CONTENT;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.CREATE_SUBSCRIPTION_RPC_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.GET_SCHEMA_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_CANDIDATE_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_COMMIT_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_DISCARD_CHANGES_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_GET_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_LOCK_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_RUNNING_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.createEditConfigStructure;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.toFilterStructure;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.toId;
import static org.opendaylight.netconf.common.mdsal.NormalizedDataUtil.NETCONF_DATA_QNAME;

import java.io.IOException;
import java.util.ArrayList;
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
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.mdsal.dom.api.DOMActionResult;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.mdsal.AbstractBaseSchemasTest;
import org.opendaylight.netconf.client.mdsal.MonitoringSchemaSourceProvider;
import org.opendaylight.netconf.common.mdsal.NormalizedDataUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.IetfNetconfService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Capabilities;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Datastores;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Sessions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Statistics;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.datastores.Datastore;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.datastores.datastore.Locks;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.datastores.datastore.locks.lock.type.partial.lock.PartialLock;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.Schema;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfConfigChange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.netconf.config.change.Edit;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.AnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
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
    private static final Absolute RESET_SERVER_PATH = Absolute.of(SERVER_QNAME, RESET_QNAME);
    private static final QName APPLICATIONS_QNAME = QName.create(URN_EXAMPLE_SERVER_FARM_2,
            REVISION_EXAMPLE_SERVER_FARM_2, "applications");
    private static final QName APPLICATION_QNAME = QName.create(APPLICATIONS_QNAME, "application");
    private static final QName KILL_QNAME = QName.create(APPLICATION_QNAME, "kill");
    private static final Absolute KILL_SERVER_APP_PATH =
            Absolute.of(SERVER_QNAME, APPLICATIONS_QNAME, APPLICATION_QNAME, KILL_QNAME);

    private static final QName DEVICE_QNAME =
            QName.create(URN_EXAMPLE_SERVER_FARM, REVISION_EXAMPLE_SERVER_FARM, "device");
    private static final QName START_QNAME = QName.create(DEVICE_QNAME, "start");
    private static final Absolute START_DEVICE_PATH = Absolute.of(DEVICE_QNAME, START_QNAME);
    private static final QName INTERFACE_QNAME = QName.create(DEVICE_QNAME, "interface");
    private static final QName ENABLE_QNAME = QName.create(INTERFACE_QNAME, "enable");
    private static final Absolute ENABLE_INTERFACE_PATH = Absolute.of(DEVICE_QNAME, INTERFACE_QNAME, ENABLE_QNAME);

    private static final QName DISABLE_QNAME = QName.create(URN_EXAMPLE_AUGMENTED_ACTION, "disable");
    private static final Absolute DISABLE_INTERFACE_PATH = Absolute.of(DEVICE_QNAME, INTERFACE_QNAME, DISABLE_QNAME);

    private static final QName CHECK_WITH_OUTPUT_QNAME =
            QName.create(URN_EXAMPLE_RPCS_ACTIONS_OUTPUTS, "check-with-output");
    private static final Absolute CHECK_WITH_OUTPUT_INTERFACE_PATH =
            Absolute.of(DEVICE_QNAME, INTERFACE_QNAME, CHECK_WITH_OUTPUT_QNAME);
    private static final QName CHECK_WITHOUT_OUTPUT_QNAME =
            QName.create(URN_EXAMPLE_RPCS_ACTIONS_OUTPUTS, "check-without-output");
    private static final Absolute CHECK_WITHOUT_OUTPUT_INTERFACE_PATH =
            Absolute.of(DEVICE_QNAME, INTERFACE_QNAME, CHECK_WITHOUT_OUTPUT_QNAME);
    private static final QName RPC_WITH_OUTPUT_QNAME =
            QName.create(URN_EXAMPLE_RPCS_ACTIONS_OUTPUTS, "rpc-with-output");
    private static final QName RPC_WITHOUT_OUTPUT_QNAME =
            QName.create(URN_EXAMPLE_RPCS_ACTIONS_OUTPUTS, "rpc-without-output");

    private static final QName BOX_OUT_QNAME =
            QName.create(URN_EXAMPLE_SERVER_FARM, REVISION_EXAMPLE_SERVER_FARM, "box-out");
    private static final QName BOX_IN_QNAME = QName.create(BOX_OUT_QNAME, "box-in");
    private static final QName OPEN_QNAME = QName.create(BOX_IN_QNAME, "open");
    private static final Absolute OPEN_BOXES_PATH = Absolute.of(BOX_OUT_QNAME, BOX_IN_QNAME, OPEN_QNAME);

    private static final QName FOO_QNAME = QName.create(URN_EXAMPLE_CONFLICT, "foo");
    private static final QName BAR_QNAME = QName.create(URN_EXAMPLE_CONFLICT, "bar");
    private static final QName XYZZY_QNAME = QName.create(URN_EXAMPLE_CONFLICT, "xyzzy");
    private static final Absolute XYZZY_FOO_PATH = Absolute.of(FOO_QNAME, XYZZY_QNAME);
    private static final Absolute XYZZY_BAR_PATH = Absolute.of(BAR_QNAME, XYZZY_QNAME);

    private static final QName CONFLICT_CHOICE_QNAME = QName.create(URN_EXAMPLE_CONFLICT, "conflict-choice");
    private static final QName CHOICE_CONT_QNAME = QName.create(URN_EXAMPLE_CONFLICT, "choice-cont");
    private static final QName CHOICE_ACTION_QNAME = QName.create(URN_EXAMPLE_CONFLICT, "choice-action");
    private static final Absolute CHOICE_ACTION_PATH =
            Absolute.of(CONFLICT_CHOICE_QNAME, CHOICE_CONT_QNAME, CHOICE_CONT_QNAME, CHOICE_ACTION_QNAME);

    private static EffectiveModelContext PARTIAL_SCHEMA;
    private static EffectiveModelContext SCHEMA;
    private static EffectiveModelContext ACTION_SCHEMA;

    private NetconfMessageTransformer actionNetconfMessageTransformer;
    private NetconfMessageTransformer netconfMessageTransformer;

    @BeforeClass
    public static void beforeClass() {
        PARTIAL_SCHEMA = BindingRuntimeHelpers.createEffectiveModel(NetconfState.class);
        SCHEMA = BindingRuntimeHelpers.createEffectiveModel(IetfNetconfService.class, NetconfState.class,
            NetconfConfigChange.class);
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
        actionNetconfMessageTransformer = new NetconfMessageTransformer(MountPointContext.of(ACTION_SCHEMA),
            true, BASE_SCHEMAS.getBaseSchema());
    }

    @Test
    public void testLockRequestBaseSchemaNotPresent() throws Exception {
        final NetconfMessageTransformer transformer = getTransformer(PARTIAL_SCHEMA);
        final NetconfMessage netconfMessage = transformer.toRpcRequest(NETCONF_LOCK_QNAME,
                NetconfBaseOps.getLockContent(NETCONF_CANDIDATE_NODEID));

        assertThat(XmlUtil.toString(netconfMessage.getDocument()), CoreMatchers.containsString("<lock"));
        assertThat(XmlUtil.toString(netconfMessage.getDocument()), CoreMatchers.containsString("<rpc"));
    }

    @Test
    public void testCreateSubscriberNotificationSchemaNotPresent() throws Exception {
        final NetconfMessageTransformer transformer = new NetconfMessageTransformer(MountPointContext.of(SCHEMA), true,
            BASE_SCHEMAS.getBaseSchemaWithNotifications());
        NetconfMessage netconfMessage = transformer.toRpcRequest(CREATE_SUBSCRIPTION_RPC_QNAME,
                CREATE_SUBSCRIPTION_RPC_CONTENT);
        String documentString = XmlUtil.toString(netconfMessage.getDocument());
        assertThat(documentString, CoreMatchers.containsString("<create-subscription"));
        assertThat(documentString, CoreMatchers.containsString("<rpc"));
    }

    @Test
    public void tesLockSchemaRequest() throws Exception {
        final NetconfMessageTransformer transformer = getTransformer(PARTIAL_SCHEMA);
        final String result = "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"><ok/></rpc-reply>";

        transformer.toRpcResult(
            RpcResultBuilder.success(new NetconfMessage(XmlUtil.readXmlToDocument(result))).build(),
            NETCONF_LOCK_QNAME);
    }

    @Test
    public void testRpcEmptyBodyWithOutputDefinedSchemaResult() throws Exception {
        final String result = "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"><ok/></rpc-reply>";

        DOMRpcResult domRpcResult = actionNetconfMessageTransformer.toRpcResult(
            RpcResultBuilder.success(new NetconfMessage(XmlUtil.readXmlToDocument(result))).build(),
            RPC_WITH_OUTPUT_QNAME);
        assertNotNull(domRpcResult);
    }

    @Test
    public void testRpcEmptyBodyWithoutOutputDefinedSchemaResult() throws Exception {
        final String result = "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"><ok/></rpc-reply>";

        DOMRpcResult domRpcResult = actionNetconfMessageTransformer.toRpcResult(
            RpcResultBuilder.success(new NetconfMessage(XmlUtil.readXmlToDocument(result))).build(),
            RPC_WITHOUT_OUTPUT_QNAME);
        assertNotNull(domRpcResult);
    }

    @Test
    public void testDiscardChangesRequest() throws Exception {
        final NetconfMessage netconfMessage =
                netconfMessageTransformer.toRpcRequest(NETCONF_DISCARD_CHANGES_QNAME, null);
        assertThat(XmlUtil.toString(netconfMessage.getDocument()), CoreMatchers.containsString("<discard"));
        assertThat(XmlUtil.toString(netconfMessage.getDocument()), CoreMatchers.containsString("<rpc"));
        assertThat(XmlUtil.toString(netconfMessage.getDocument()), CoreMatchers.containsString("message-id"));
    }

    @Test
    public void testGetSchemaRequest() throws Exception {
        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(GET_SCHEMA_QNAME,
                MonitoringSchemaSourceProvider.createGetSchemaRequest("module", Optional.of("2012-12-12")));
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
        final DOMRpcResult compositeNodeRpcResult = transformer.toRpcResult(RpcResultBuilder.success(response).build(),
            GET_SCHEMA_QNAME);
        assertTrue(compositeNodeRpcResult.errors().isEmpty());
        assertNotNull(compositeNodeRpcResult.value());
        final DOMSource schemaContent = ((DOMSourceAnyxmlNode) compositeNodeRpcResult.value()
                .body().iterator().next()).body();
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
        final DOMRpcResult compositeNodeRpcResult = transformer.toRpcResult(RpcResultBuilder.success(response).build(),
            NETCONF_GET_CONFIG_QNAME);
        assertTrue(compositeNodeRpcResult.errors().isEmpty());
        assertNotNull(compositeNodeRpcResult.value());

        final var values = MonitoringSchemaSourceProvider.createGetSchemaRequest(
            "module", Optional.of("2012-12-12")).body();

        final Map<QName, Object> keys = new HashMap<>();
        for (final DataContainerChild value : values) {
            keys.put(value.name().getNodeType(), value.body());
        }

        final NodeIdentifierWithPredicates identifierWithPredicates =
                NodeIdentifierWithPredicates.of(Schema.QNAME, keys);
        final MapEntryNode schemaNode =
                Builders.mapEntryBuilder().withNodeIdentifier(identifierWithPredicates).withValue(values).build();

        final DOMSourceAnyxmlNode data = (DOMSourceAnyxmlNode) compositeNodeRpcResult.value()
                .findChildByArg(toId(NETCONF_DATA_QNAME)).orElseThrow();

        var nodeResult = NormalizedDataUtil.transformDOMSourceToNormalizedNode(SCHEMA, data.body());
        ContainerNode result = (ContainerNode) nodeResult.getResult().data();
        final ContainerNode state = (ContainerNode) result.getChildByArg(toId(NetconfState.QNAME));
        final ContainerNode schemas = (ContainerNode) state.getChildByArg(toId(Schemas.QNAME));
        final MapNode schemaParent = (MapNode) schemas.getChildByArg(toId(Schema.QNAME));
        assertEquals(1, schemaParent.body().size());

        assertEquals(schemaNode, schemaParent.body().iterator().next());
    }

    @Test
    public void testGetConfigLeafRequest() throws Exception {
        final AnyxmlNode<?> filter = toFilterStructure(
                YangInstanceIdentifier.of(toId(NetconfState.QNAME), toId(Schemas.QNAME), toId(Schema.QNAME),
                    NodeIdentifierWithPredicates.of(Schema.QNAME),
                    toId(QName.create(Schemas.QNAME, "version"))), SCHEMA);

        final ContainerNode source = NetconfBaseOps.getSourceNode(NETCONF_RUNNING_NODEID);

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(NETCONF_GET_CONFIG_QNAME,
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
        final AnyxmlNode<?> filter = toFilterStructure(
                YangInstanceIdentifier.of(toId(NetconfState.QNAME), toId(Schemas.QNAME)), SCHEMA);

        final ContainerNode source = NetconfBaseOps.getSourceNode(NETCONF_RUNNING_NODEID);

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(NETCONF_GET_CONFIG_QNAME,
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
        final var values = MonitoringSchemaSourceProvider.createGetSchemaRequest(
            "module", Optional.of("2012-12-12")).body();

        final Map<QName, Object> keys = new HashMap<>();
        for (final DataContainerChild value : values) {
            keys.put(value.name().getNodeType(), value.body());
        }

        final NodeIdentifierWithPredicates identifierWithPredicates =
                NodeIdentifierWithPredicates.of(Schema.QNAME, keys);
        final MapEntryNode schemaNode =
                Builders.mapEntryBuilder().withNodeIdentifier(identifierWithPredicates).withValue(values).build();

        final YangInstanceIdentifier id = YangInstanceIdentifier.builder()
                .node(NetconfState.QNAME).node(Schemas.QNAME).node(Schema.QNAME)
                .nodeWithKey(Schema.QNAME, keys).build();
        final DataContainerChild editConfigStructure =
                createEditConfigStructure(BASE_SCHEMAS.getBaseSchemaWithNotifications().getEffectiveModelContext(), id,
                    Optional.empty(), Optional.ofNullable(schemaNode));

        final DataContainerChild target = NetconfBaseOps.getTargetNode(NETCONF_CANDIDATE_NODEID);

        final ContainerNode wrap =
                NetconfMessageTransformUtil.wrap(NETCONF_EDIT_CONFIG_QNAME, editConfigStructure, target);
        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(NETCONF_EDIT_CONFIG_QNAME, wrap);

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
        final DataContainerChild filter = toFilterStructure(
                YangInstanceIdentifier.of(toId(NetconfState.QNAME), toId(Capabilities.QNAME), toId(capability),
                    new NodeWithValue<>(capability, "a:b:c")), SCHEMA);

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(NETCONF_GET_QNAME,
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

    @Test
    public void testGetLeafList() throws IOException, SAXException {
        final YangInstanceIdentifier path = YangInstanceIdentifier.of(
                NetconfState.QNAME,
                Capabilities.QNAME,
                QName.create(Capabilities.QNAME, "capability"));
        final DataContainerChild filter = toFilterStructure(path, SCHEMA);
        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(NETCONF_GET_QNAME,
                NetconfMessageTransformUtil.wrap(toId(NETCONF_GET_QNAME), filter));

        assertSimilarXml(netconfMessage, "<rpc message-id=\"m-0\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                + "<get>\n"
                + "<filter xmlns:ns0=\"urn:ietf:params:xml:ns:netconf:base:1.0\" ns0:type=\"subtree\">\n"
                + "<netconf-state xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\">\n"
                + "<capabilities>\n"
                + "<capability/>\n"
                + "</capabilities>\n"
                + "</netconf-state>\n"
                + "</filter>\n"
                + "</get>\n"
                + "</rpc>\n");
    }

    @Test
    public void testGetList() throws IOException, SAXException {
        final YangInstanceIdentifier path = YangInstanceIdentifier.of(
                NetconfState.QNAME,
                Datastores.QNAME,
                QName.create(Datastores.QNAME, "datastore"));
        final DataContainerChild filter = toFilterStructure(path, SCHEMA);
        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(NETCONF_GET_QNAME,
                NetconfMessageTransformUtil.wrap(toId(NETCONF_GET_QNAME), filter));

        assertSimilarXml(netconfMessage, "<rpc message-id=\"m-0\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                + "<get>\n"
                + "<filter xmlns:ns0=\"urn:ietf:params:xml:ns:netconf:base:1.0\" ns0:type=\"subtree\">\n"
                + "<netconf-state xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\">\n"
                + "<datastores>\n"
                + "<datastore/>\n"
                + "</datastores>\n"
                + "</netconf-state>\n"
                + "</filter>\n"
                + "</get>\n"
                + "</rpc>\n");
    }

    private static NetconfMessageTransformer getTransformer(final EffectiveModelContext schema) {
        return new NetconfMessageTransformer(MountPointContext.of(schema), true, BASE_SCHEMAS.getBaseSchema());
    }

    @Test
    public void testCommitResponse() throws Exception {
        final NetconfMessage response = new NetconfMessage(XmlUtil.readXmlToDocument(
                "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"><ok/></rpc-reply>"
        ));
        final DOMRpcResult compositeNodeRpcResult = netconfMessageTransformer.toRpcResult(
            RpcResultBuilder.success(response).build(),
            NETCONF_COMMIT_QNAME);
        assertTrue(compositeNodeRpcResult.errors().isEmpty());
        assertNull(compositeNodeRpcResult.value());
    }

    @Test
    public void getActionsTest() {
        Set<Absolute> schemaPaths = new HashSet<>();
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

        var actions = NetconfMessageTransformer.getActions(ACTION_SCHEMA);
        assertEquals(schemaPaths.size(), actions.size());

        for (var path : schemaPaths) {
            assertNotNull("Action for " + path + " not found", actions.get(path));
        }
    }

    @Test
    public void toActionRequestListTopLevelTest() {
        QName nameQname = QName.create(SERVER_QNAME, "name");
        List<PathArgument> nodeIdentifiers = new ArrayList<>();
        nodeIdentifiers.add(new NodeIdentifier(SERVER_QNAME));
        nodeIdentifiers.add(NodeIdentifierWithPredicates.of(SERVER_QNAME, nameQname, "test"));
        DOMDataTreeIdentifier domDataTreeIdentifier = prepareDataTreeId(nodeIdentifiers);

        ContainerNode data = initInputAction(QName.create(SERVER_QNAME, "reset-at"), "now");

        NetconfMessage actionRequest = actionNetconfMessageTransformer.toActionRequest(
                RESET_SERVER_PATH, domDataTreeIdentifier, data);

        Node childAction = checkBasePartOfActionRequest(actionRequest);

        Node childServer = childAction.getFirstChild();
        checkNode(childServer, "server", "server", URN_EXAMPLE_SERVER_FARM);

        Node childName = childServer.getFirstChild();
        checkNode(childName, "name", "name", URN_EXAMPLE_SERVER_FARM);

        Node childTest = childName.getFirstChild();
        assertEquals(childTest.getNodeValue(), "test");

        checkAction(RESET_QNAME, childName.getNextSibling(), "reset-at", "reset-at", "now");
    }

    @Test
    public void toActionRequestContainerTopLevelTest() {
        List<PathArgument> nodeIdentifiers = List.of(NodeIdentifier.create(DEVICE_QNAME));
        DOMDataTreeIdentifier domDataTreeIdentifier = prepareDataTreeId(nodeIdentifiers);

        ContainerNode payload = initInputAction(QName.create(DEVICE_QNAME, "start-at"), "now");
        NetconfMessage actionRequest = actionNetconfMessageTransformer.toActionRequest(
                START_DEVICE_PATH, domDataTreeIdentifier, payload);

        Node childAction = checkBasePartOfActionRequest(actionRequest);

        Node childDevice = childAction.getFirstChild();
        checkNode(childDevice, "device", "device", URN_EXAMPLE_SERVER_FARM);

        checkAction(START_QNAME, childDevice.getFirstChild(), "start-at", "start-at", "now");
    }

    @Test
    public void toActionRequestContainerInContainerTest() {
        List<PathArgument> nodeIdentifiers = new ArrayList<>();
        nodeIdentifiers.add(NodeIdentifier.create(BOX_OUT_QNAME));
        nodeIdentifiers.add(NodeIdentifier.create(BOX_IN_QNAME));

        DOMDataTreeIdentifier domDataTreeIdentifier = prepareDataTreeId(nodeIdentifiers);

        ContainerNode payload = initInputAction(QName.create(BOX_OUT_QNAME, "start-at"), "now");
        NetconfMessage actionRequest = actionNetconfMessageTransformer.toActionRequest(
                OPEN_BOXES_PATH, domDataTreeIdentifier, payload);

        Node childAction = checkBasePartOfActionRequest(actionRequest);

        Node childBoxOut = childAction.getFirstChild();
        checkNode(childBoxOut, "box-out", "box-out", URN_EXAMPLE_SERVER_FARM);

        Node childBoxIn = childBoxOut.getFirstChild();
        checkNode(childBoxIn, "box-in", "box-in", URN_EXAMPLE_SERVER_FARM);

        Node action = childBoxIn.getFirstChild();
        checkNode(action, OPEN_QNAME.getLocalName(), OPEN_QNAME.getLocalName(), OPEN_QNAME.getNamespace().toString());
    }

    @Test
    public void toActionRequestListInContainerTest() {
        QName nameQname = QName.create(INTERFACE_QNAME, "name");

        List<PathArgument> nodeIdentifiers = new ArrayList<>();
        nodeIdentifiers.add(NodeIdentifier.create(DEVICE_QNAME));
        nodeIdentifiers.add(NodeIdentifier.create(INTERFACE_QNAME));
        nodeIdentifiers.add(NodeIdentifierWithPredicates.of(INTERFACE_QNAME, nameQname, "test"));

        DOMDataTreeIdentifier domDataTreeIdentifier = prepareDataTreeId(nodeIdentifiers);

        ContainerNode payload = initEmptyInputAction(INTERFACE_QNAME);
        NetconfMessage actionRequest = actionNetconfMessageTransformer.toActionRequest(
                ENABLE_INTERFACE_PATH, domDataTreeIdentifier, payload);

        Node childAction = checkBasePartOfActionRequest(actionRequest);

        Node childDevice = childAction.getFirstChild();
        checkNode(childDevice, "device", "device", URN_EXAMPLE_SERVER_FARM);

        Node childInterface = childDevice.getFirstChild();
        checkNode(childInterface, "interface", "interface", URN_EXAMPLE_SERVER_FARM);

        Node childName = childInterface.getFirstChild();
        checkNode(childName, "name", "name", nameQname.getNamespace().toString());

        Node childTest = childName.getFirstChild();
        assertEquals(childTest.getNodeValue(), "test");

        Node action = childInterface.getLastChild();
        checkNode(action, ENABLE_QNAME.getLocalName(), ENABLE_QNAME.getLocalName(),
                ENABLE_QNAME.getNamespace().toString());
    }

    @Test
    public void toActionRequestListInContainerAugmentedIntoListTest() {
        QName serverNameQname = QName.create(SERVER_QNAME, "name");
        QName applicationNameQname = QName.create(APPLICATION_QNAME, "name");

        List<PathArgument> nodeIdentifiers = new ArrayList<>();
        nodeIdentifiers.add(NodeIdentifier.create(SERVER_QNAME));
        nodeIdentifiers.add(NodeIdentifierWithPredicates.of(SERVER_QNAME, serverNameQname, "testServer"));
        nodeIdentifiers.add(NodeIdentifier.create(APPLICATIONS_QNAME));
        nodeIdentifiers.add(NodeIdentifier.create(APPLICATION_QNAME));
        nodeIdentifiers.add(NodeIdentifierWithPredicates.of(APPLICATION_QNAME,
                applicationNameQname, "testApplication"));

        DOMDataTreeIdentifier domDataTreeIdentifier = prepareDataTreeId(nodeIdentifiers);

        ContainerNode payload = initEmptyInputAction(APPLICATION_QNAME);
        NetconfMessage actionRequest = actionNetconfMessageTransformer.toActionRequest(
                KILL_SERVER_APP_PATH, domDataTreeIdentifier, payload);

        Node childAction = checkBasePartOfActionRequest(actionRequest);

        Node childServer = childAction.getFirstChild();
        checkNode(childServer, "server", "server", URN_EXAMPLE_SERVER_FARM);

        Node childServerName = childServer.getFirstChild();
        checkNode(childServerName, "name", "name", URN_EXAMPLE_SERVER_FARM);

        Node childServerNameTest = childServerName.getFirstChild();
        assertEquals(childServerNameTest.getNodeValue(), "testServer");

        Node childApplications = childServer.getLastChild();
        checkNode(childApplications, "applications", "applications", URN_EXAMPLE_SERVER_FARM_2);

        Node childApplication = childApplications.getFirstChild();
        checkNode(childApplication, "application", "application", URN_EXAMPLE_SERVER_FARM_2);

        Node childApplicationName = childApplication.getFirstChild();
        checkNode(childApplicationName, "name", "name", URN_EXAMPLE_SERVER_FARM_2);

        Node childApplicationNameTest = childApplicationName.getFirstChild();
        assertEquals(childApplicationNameTest.getNodeValue(), "testApplication");

        Node childKillAction = childApplication.getLastChild();
        checkNode(childApplication, "application", "application", URN_EXAMPLE_SERVER_FARM_2);
        checkNode(childKillAction, KILL_QNAME.getLocalName(), KILL_QNAME.getLocalName(),
                KILL_QNAME.getNamespace().toString());
    }

    @Test
    public void toActionRequestConflictingInListTest() {
        QName barInputQname = QName.create(BAR_QNAME, "bar");
        QName barIdQname = QName.create(BAR_QNAME, "bar-id");
        Byte barInput = 1;

        List<PathArgument> nodeIdentifiers = new ArrayList<>();
        nodeIdentifiers.add(NodeIdentifier.create(BAR_QNAME));
        nodeIdentifiers.add(NodeIdentifierWithPredicates.of(BAR_QNAME, barIdQname, "test"));

        DOMDataTreeIdentifier domDataTreeIdentifier = prepareDataTreeId(nodeIdentifiers);

        ContainerNode payload = Builders.containerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(QName.create(barInputQname, "input")))
            .withChild(ImmutableNodes.leafNode(barInputQname, barInput))
            .build();

        NetconfMessage actionRequest = actionNetconfMessageTransformer.toActionRequest(
                XYZZY_BAR_PATH, domDataTreeIdentifier, payload);

        Node childAction = checkBasePartOfActionRequest(actionRequest);

        Node childBar = childAction.getFirstChild();
        checkNode(childBar, "bar", "bar", URN_EXAMPLE_CONFLICT);

        Node childBarId = childBar.getFirstChild();
        checkNode(childBarId, "bar-id", "bar-id", URN_EXAMPLE_CONFLICT);

        Node childTest = childBarId.getFirstChild();
        assertEquals(childTest.getNodeValue(), "test");

        Node action = childBar.getLastChild();
        checkNode(action, XYZZY_QNAME.getLocalName(), XYZZY_QNAME.getLocalName(),
                XYZZY_QNAME.getNamespace().toString());
    }

    @Test
    public void toActionRequestConflictingInContainerTest() {
        QName fooInputQname = QName.create(FOO_QNAME, "foo");

        List<PathArgument> nodeIdentifiers = new ArrayList<>();
        nodeIdentifiers.add(NodeIdentifier.create(FOO_QNAME));
        DOMDataTreeIdentifier domDataTreeIdentifier = prepareDataTreeId(nodeIdentifiers);
        ContainerNode payload = initInputAction(fooInputQname, "test");

        NetconfMessage actionRequest = actionNetconfMessageTransformer.toActionRequest(
                XYZZY_FOO_PATH, domDataTreeIdentifier, payload);

        Node childAction = checkBasePartOfActionRequest(actionRequest);

        Node childBar = childAction.getFirstChild();
        checkNode(childBar, "foo", "foo", URN_EXAMPLE_CONFLICT);

        Node action = childBar.getLastChild();
        checkNode(action, XYZZY_QNAME.getLocalName(), XYZZY_QNAME.getLocalName(),
                XYZZY_QNAME.getNamespace().toString());
    }

    @Test
    public void toActionRequestChoiceTest() {
        List<PathArgument> nodeIdentifiers = new ArrayList<>();
        nodeIdentifiers.add(NodeIdentifier.create(CONFLICT_CHOICE_QNAME));
        nodeIdentifiers.add(NodeIdentifier.create(CHOICE_CONT_QNAME));
        DOMDataTreeIdentifier domDataTreeIdentifier = prepareDataTreeId(nodeIdentifiers);
        NormalizedNode payload = initEmptyInputAction(CHOICE_ACTION_QNAME);

        NetconfMessage actionRequest = actionNetconfMessageTransformer.toActionRequest(
                CHOICE_ACTION_PATH, domDataTreeIdentifier, payload);

        Node childAction = checkBasePartOfActionRequest(actionRequest);

        Node childChoiceCont = childAction.getFirstChild();
        checkNode(childChoiceCont, "choice-cont", "choice-cont", URN_EXAMPLE_CONFLICT);

        Node action = childChoiceCont.getLastChild();
        checkNode(action, CHOICE_ACTION_QNAME.getLocalName(), CHOICE_ACTION_QNAME.getLocalName(),
                CHOICE_ACTION_QNAME.getNamespace().toString());
    }

    @Test
    public void toAugmentedActionRequestListInContainerTest() {
        QName nameQname = QName.create(INTERFACE_QNAME, "name");

        List<PathArgument> nodeIdentifiers = new ArrayList<>();
        nodeIdentifiers.add(NodeIdentifier.create(DEVICE_QNAME));
        nodeIdentifiers.add(NodeIdentifier.create(INTERFACE_QNAME));
        nodeIdentifiers.add(NodeIdentifierWithPredicates.of(INTERFACE_QNAME, nameQname, "test"));

        DOMDataTreeIdentifier domDataTreeIdentifier = prepareDataTreeId(nodeIdentifiers);

        NormalizedNode payload = initEmptyInputAction(INTERFACE_QNAME);
        NetconfMessage actionRequest = actionNetconfMessageTransformer.toActionRequest(
                DISABLE_INTERFACE_PATH, domDataTreeIdentifier, payload);

        Node childAction = checkBasePartOfActionRequest(actionRequest);

        Node childDevice = childAction.getFirstChild();
        checkNode(childDevice, "device", "device", URN_EXAMPLE_SERVER_FARM);

        Node childInterface = childDevice.getFirstChild();
        checkNode(childInterface, "interface", "interface", URN_EXAMPLE_SERVER_FARM);

        Node childName = childInterface.getFirstChild();
        checkNode(childName, "name", "name", nameQname.getNamespace().toString());

        Node childTest = childName.getFirstChild();
        assertEquals(childTest.getNodeValue(), "test");

        Node action = childInterface.getLastChild();
        checkNode(action, DISABLE_QNAME.getLocalName(), DISABLE_QNAME.getLocalName(),
                DISABLE_QNAME.getNamespace().toString());
    }

    @Test
    public void toActionResultTest() throws Exception {
        NetconfMessage message = new NetconfMessage(XmlUtil.readXmlToDocument(
                "<rpc-reply message-id=\"101\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">"
                + "<reset-finished-at xmlns=\"urn:example:server-farm\">"
                + "now"
                + "</reset-finished-at>"
                + "</rpc-reply>"));
        DOMActionResult actionResult = actionNetconfMessageTransformer.toActionResult(RESET_SERVER_PATH, message);
        assertNotNull(actionResult);
        ContainerNode containerNode = actionResult.getOutput().orElseThrow();
        assertNotNull(containerNode);
        assertEquals("now", containerNode.body().iterator().next().body());
    }

    @Test
    public void toActionEmptyBodyWithOutputDefinedResultTest() throws Exception {
        NetconfMessage message = new NetconfMessage(XmlUtil.readXmlToDocument(
                "<rpc-reply message-id=\"101\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">"
                + "<ok/>"
                + "</rpc-reply>"));
        DOMActionResult actionResult =
                actionNetconfMessageTransformer.toActionResult(CHECK_WITH_OUTPUT_INTERFACE_PATH, message);
        assertNotNull(actionResult);
        assertTrue(actionResult.getOutput().isEmpty());
    }

    @Test
    public void toActionEmptyBodyWithoutOutputDefinedResultTest() throws Exception {
        NetconfMessage message = new NetconfMessage(XmlUtil.readXmlToDocument(
                "<rpc-reply message-id=\"101\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">"
                + "<ok/>"
                + "</rpc-reply>"));
        DOMActionResult actionResult =
                actionNetconfMessageTransformer.toActionResult(CHECK_WITHOUT_OUTPUT_INTERFACE_PATH, message);
        assertNotNull(actionResult);
        assertTrue(actionResult.getOutput().isEmpty());
    }

    @Test
    public void getTwoNonOverlappingFieldsTest() throws IOException, SAXException {
        // preparation of the fields
        final YangInstanceIdentifier parentYiid = YangInstanceIdentifier.of(NetconfState.QNAME);
        final YangInstanceIdentifier netconfStartTimeField = YangInstanceIdentifier.of(Statistics.QNAME,
                QName.create(Statistics.QNAME, "netconf-start-time"));
        final YangInstanceIdentifier datastoresField = YangInstanceIdentifier.of(Datastores.QNAME);

        // building filter structure and NETCONF message
        final AnyxmlNode<?> filterStructure = toFilterStructure(
            List.of(FieldsFilter.of(parentYiid, List.of(netconfStartTimeField, datastoresField))), SCHEMA);
        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(NETCONF_GET_QNAME,
                NetconfMessageTransformUtil.wrap(toId(NETCONF_GET_QNAME), filterStructure));

        // testing
        assertSimilarXml(netconfMessage, "<rpc message-id=\"m-0\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                + "<get>\n"
                + "<filter xmlns:ns0=\"urn:ietf:params:xml:ns:netconf:base:1.0\" ns0:type=\"subtree\">\n"
                + "<netconf-state xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\">\n"
                + "<statistics>\n"
                + "<netconf-start-time/>\n"
                + "</statistics>\n"
                + "<datastores/>\n"
                + "</netconf-state>\n"
                + "</filter>\n"
                + "</get>\n"
                + "</rpc>");
    }

    @Test
    public void getOverlappingFieldsTest() throws IOException, SAXException {
        // preparation of the fields
        final YangInstanceIdentifier parentYiid = YangInstanceIdentifier.of(NetconfState.QNAME);
        final YangInstanceIdentifier capabilitiesField = YangInstanceIdentifier.of(Capabilities.QNAME);
        final YangInstanceIdentifier capabilityField = YangInstanceIdentifier.of(Capabilities.QNAME,
                QName.create(Capabilities.QNAME, "capability"));
        final YangInstanceIdentifier datastoreField = YangInstanceIdentifier.of(Datastores.QNAME);
        final YangInstanceIdentifier locksFields = YangInstanceIdentifier.of(toId(Datastores.QNAME),
                toId(Datastore.QNAME), NodeIdentifierWithPredicates.of(Datastore.QNAME), toId(Locks.QNAME));

        // building filter structure and NETCONF message
        final AnyxmlNode<?> filterStructure = toFilterStructure(
                List.of(FieldsFilter.of(parentYiid,
                    List.of(capabilitiesField, capabilityField, datastoreField, locksFields))),
                SCHEMA);
        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(NETCONF_GET_QNAME,
                NetconfMessageTransformUtil.wrap(toId(NETCONF_GET_QNAME), filterStructure));

        // testing
        assertSimilarXml(netconfMessage, "<rpc message-id=\"m-0\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                + "<get>\n"
                + "<filter xmlns:ns0=\"urn:ietf:params:xml:ns:netconf:base:1.0\" ns0:type=\"subtree\">\n"
                + "<netconf-state xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\">\n"
                + "<capabilities/>\n"
                + "<datastores/>\n"
                + "</netconf-state>\n"
                + "</filter>\n"
                + "</get>\n"
                + "</rpc>");
    }

    @Test
    public void getOverlappingFieldsWithEmptyFieldTest() throws IOException, SAXException {
        // preparation of the fields
        final YangInstanceIdentifier parentYiid = YangInstanceIdentifier.of(NetconfState.QNAME);
        final YangInstanceIdentifier capabilitiesField = YangInstanceIdentifier.of(Capabilities.QNAME);

        // building filter structure and NETCONF message
        final AnyxmlNode<?> filterStructure = toFilterStructure(
                List.of(FieldsFilter.of(parentYiid, List.of(capabilitiesField, YangInstanceIdentifier.of()))),
                SCHEMA);
        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(NETCONF_GET_QNAME,
                NetconfMessageTransformUtil.wrap(toId(NETCONF_GET_QNAME), filterStructure));

        // testing
        assertSimilarXml(netconfMessage, "<rpc message-id=\"m-0\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                + "<get>\n"
                + "<filter xmlns:ns0=\"urn:ietf:params:xml:ns:netconf:base:1.0\" ns0:type=\"subtree\">\n"
                + "<netconf-state xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\"/>\n"
                + "</filter>\n"
                + "</get>\n"
                + "</rpc>");
    }

    @Test
    public void getSpecificFieldsUnderListTest() throws IOException, SAXException {
        // preparation of the fields
        final YangInstanceIdentifier parentYiid = YangInstanceIdentifier.of(toId(NetconfState.QNAME),
                toId(Schemas.QNAME), toId(Schema.QNAME), NodeIdentifierWithPredicates.of(Schema.QNAME));
        final YangInstanceIdentifier versionField = YangInstanceIdentifier.of(
                QName.create(Schema.QNAME, "version"));
        final YangInstanceIdentifier identifierField = YangInstanceIdentifier.of(
                QName.create(Schema.QNAME, "namespace"));

        // building filter structure and NETCONF message
        final AnyxmlNode<?> filterStructure = toFilterStructure(
            List.of(FieldsFilter.of(parentYiid, List.of(versionField, identifierField))), SCHEMA);
        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(NETCONF_GET_QNAME,
                NetconfMessageTransformUtil.wrap(toId(NETCONF_GET_QNAME), filterStructure));

        // testing
        assertSimilarXml(netconfMessage, "<rpc message-id=\"m-0\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                + "<get>\n"
                + "<filter xmlns:ns0=\"urn:ietf:params:xml:ns:netconf:base:1.0\" ns0:type=\"subtree\">\n"
                + "<netconf-state xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\">\n"
                + "<schemas>\n"
                + "<schema>\n"
                + "<version/>\n"
                + "<namespace/>\n"
                // explicitly fetched list keys - identifier and format
                + "<identifier/>\n"
                + "<format/>\n"
                + "</schema>\n"
                + "</schemas>\n"
                + "</netconf-state>\n"
                + "</filter>\n"
                + "</get>\n"
                + "</rpc>");
    }

    @Test
    public void getSpecificFieldsUnderMultipleLists() throws IOException, SAXException {
        // preparation of the fields
        final YangInstanceIdentifier parentYiid = YangInstanceIdentifier.of(NetconfState.QNAME, Datastores.QNAME);
        final YangInstanceIdentifier partialLockYiid = YangInstanceIdentifier.of(toId(Datastore.QNAME),
                NodeIdentifierWithPredicates.of(Datastore.QNAME), toId(Locks.QNAME),
                toId(QName.create(Locks.QNAME, "lock-type").intern()), toId(PartialLock.QNAME),
                NodeIdentifierWithPredicates.of(PartialLock.QNAME));
        final YangInstanceIdentifier lockedTimeField = partialLockYiid.node(
                QName.create(Locks.QNAME, "locked-time").intern());
        final YangInstanceIdentifier lockedBySessionField = partialLockYiid.node(
                QName.create(Locks.QNAME, "locked-by-session").intern());

        // building filter structure and NETCONF message
        final AnyxmlNode<?> filterStructure = toFilterStructure(
            List.of(FieldsFilter.of(parentYiid, List.of(lockedTimeField, lockedBySessionField))),
            SCHEMA);
        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(NETCONF_GET_QNAME,
                NetconfMessageTransformUtil.wrap(toId(NETCONF_GET_QNAME), filterStructure));

        // testing
        assertSimilarXml(netconfMessage, "<rpc message-id=\"m-0\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                + "<get>\n"
                + "<filter xmlns:ns0=\"urn:ietf:params:xml:ns:netconf:base:1.0\" ns0:type=\"subtree\">\n"
                + "<netconf-state xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\">\n"
                + "<datastores>\n"
                + "<datastore>\n"
                + "<locks>\n"
                + "<partial-lock>\n"
                + "<locked-time/>\n"
                + "<locked-by-session/>\n"
                + "<lock-id/>\n"
                + "</partial-lock>\n"
                + "</locks>\n"
                + "<name/>\n"
                + "</datastore>\n"
                + "</datastores>\n"
                + "</netconf-state>\n"
                + "</filter>\n"
                + "</get>\n"
                + "</rpc>");
    }

    @Test
    public void getWholeListsUsingFieldsTest() throws IOException, SAXException {
        // preparation of the fields
        final YangInstanceIdentifier parentYiid = YangInstanceIdentifier.of(NetconfState.QNAME);
        final YangInstanceIdentifier datastoreListField = YangInstanceIdentifier.of(toId(Datastores.QNAME),
                toId(Datastore.QNAME), NodeIdentifierWithPredicates.of(Datastore.QNAME));
        final YangInstanceIdentifier sessionListField = YangInstanceIdentifier.of(toId(Sessions.QNAME),
                toId(Session.QNAME), NodeIdentifierWithPredicates.of(Session.QNAME));

        // building filter structure and NETCONF message
        final AnyxmlNode<?> filterStructure = toFilterStructure(
                List.of(FieldsFilter.of(parentYiid, List.of(datastoreListField, sessionListField))), SCHEMA);
        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(NETCONF_GET_QNAME,
                NetconfMessageTransformUtil.wrap(toId(NETCONF_GET_QNAME), filterStructure));

        // testing
        assertSimilarXml(netconfMessage, "<rpc message-id=\"m-0\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                + "<get>\n"
                + "<filter xmlns:ns0=\"urn:ietf:params:xml:ns:netconf:base:1.0\" ns0:type=\"subtree\">\n"
                + "<netconf-state xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\">\n"
                + "<datastores>\n"
                + "<datastore/>\n"
                + "</datastores>\n"
                + "<sessions>\n"
                + "<session/>\n"
                + "</sessions>\n"
                + "</netconf-state>\n"
                + "</filter>\n"
                + "</get>\n"
                + "</rpc>");
    }

    @Test
    public void getSpecificListEntriesWithSpecificFieldsTest() throws IOException, SAXException {
        // preparation of the fields
        final YangInstanceIdentifier parentYiid = YangInstanceIdentifier.of(NetconfState.QNAME, Sessions.QNAME);
        final QName sessionId = QName.create(Session.QNAME, "session-id").intern();
        final YangInstanceIdentifier session1Field = YangInstanceIdentifier.of(toId(Session.QNAME),
                NodeIdentifierWithPredicates.of(Session.QNAME, sessionId, 1));
        final YangInstanceIdentifier session2TransportField = YangInstanceIdentifier.of(toId(Session.QNAME),
                NodeIdentifierWithPredicates.of(Session.QNAME, sessionId, 2),
                toId(QName.create(Session.QNAME, "transport").intern()));

        // building filter structure and NETCONF message
        final AnyxmlNode<?> filterStructure = toFilterStructure(
                List.of(FieldsFilter.of(parentYiid, List.of(session1Field, session2TransportField))), SCHEMA);
        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(NETCONF_GET_QNAME,
                NetconfMessageTransformUtil.wrap(toId(NETCONF_GET_QNAME), filterStructure));

        // testing
        assertSimilarXml(netconfMessage, "<rpc message-id=\"m-0\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                + "<get>\n"
                + "<filter xmlns:ns0=\"urn:ietf:params:xml:ns:netconf:base:1.0\" ns0:type=\"subtree\">\n"
                + "<netconf-state xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\">\n"
                + "<sessions>\n"
                + "<session>\n"
                + "<session-id>1</session-id>\n"
                + "</session>\n"
                + "<session>\n"
                + "<session-id>2</session-id>\n"
                + "<transport/>\n"
                + "</session>\n"
                + "</sessions>\n"
                + "</netconf-state>\n"
                + "</filter>\n"
                + "</get>\n"
                + "</rpc>");
    }

    @Test
    // Proof that YANGTOOLS-1362 works on DOM level
    public void testConfigChangeToNotification() throws SAXException, IOException {
        final var message = new NetconfMessage(XmlUtil.readXmlToDocument(
            "<notification xmlns=\"urn:ietf:params:xml:ns:netconf:notification:1.0\">\n"
            + " <eventTime>2021-11-11T11:26:16Z</eventTime> \n"
            + "  <netconf-config-change xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-notifications\">\n"
            + "     <changed-by> \n"
            + "       <username>root</username> \n"
            + "       <session-id>3</session-id> \n"
            + "     </changed-by> \n"
            + "     <datastore>running</datastore> \n"
            + "     <edit> \n"
            + "        <target xmlns:ncm=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\">/ncm:netconf-state"
            + "/ncm:datastores/ncm:datastore[ncm:name='running']</target>\n"
            + "        <operation>replace</operation> \n"
            + "     </edit> \n"
            + "  </netconf-config-change> \n"
            + "</notification>"));

        final var change = netconfMessageTransformer.toNotification(message).getBody();
        final var editList = change.getChildByArg(new NodeIdentifier(Edit.QNAME));
        assertThat(editList, instanceOf(UnkeyedListNode.class));
        final var edits = ((UnkeyedListNode) editList).body();
        assertEquals(1, edits.size());
        final var edit = edits.iterator().next();
        final var target = edit.getChildByArg(new NodeIdentifier(QName.create(Edit.QNAME, "target"))).body();
        assertThat(target, instanceOf(YangInstanceIdentifier.class));

        final var args = ((YangInstanceIdentifier) target).getPathArguments();
        assertEquals(4, args.size());
    }

    private static void checkAction(final QName actionQname, final Node action , final String inputLocalName,
            final String inputNodeName, final String inputValue) {
        checkNode(action, actionQname.getLocalName(), actionQname.getLocalName(),
                actionQname.getNamespace().toString());

        Node childResetAt = action.getFirstChild();
        checkNode(childResetAt, inputLocalName, inputNodeName, actionQname.getNamespace().toString());

        Node firstChild = childResetAt.getFirstChild();
        assertEquals(firstChild.getNodeValue(), inputValue);
    }

    private static Node checkBasePartOfActionRequest(final NetconfMessage actionRequest) {
        Node baseRpc = actionRequest.getDocument().getFirstChild();
        checkNode(baseRpc, "rpc", "rpc", NormalizedDataUtil.NETCONF_QNAME.getNamespace().toString());
        assertTrue(baseRpc.getLocalName().equals("rpc"));
        assertTrue(baseRpc.getNodeName().equals("rpc"));

        Node messageId = baseRpc.getAttributes().getNamedItem("message-id");
        assertNotNull(messageId);
        assertTrue(messageId.getNodeValue().contains("m-"));
        Node childAction = baseRpc.getFirstChild();

        checkNode(childAction, "action", "action", "urn:ietf:params:xml:ns:yang:1");
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
        return Builders.containerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(QName.create(qname, "input")))
            .withChild(ImmutableNodes.leafNode(qname, value))
            .build();
    }

    private static ContainerNode initEmptyInputAction(final QName qname) {
        return Builders.containerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(QName.create(qname, "input")))
            .build();
    }

    private static void checkNode(final Node childServer, final String expectedLocalName, final String expectedNodeName,
            final String expectedNamespace) {
        assertNotNull(childServer);
        assertEquals(childServer.getLocalName(), expectedLocalName);
        assertEquals(childServer.getNodeName(), expectedNodeName);
        assertEquals(childServer.getNamespaceURI(), expectedNamespace);
    }
}
