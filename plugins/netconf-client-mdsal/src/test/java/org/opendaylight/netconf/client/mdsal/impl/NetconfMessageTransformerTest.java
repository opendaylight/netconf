/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.EDIT_CONTENT_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_CANDIDATE_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_GET_CONFIG_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_GET_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_RUNNING_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.createEditConfigAnyxml;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.createEditConfigStructure;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.toFilterStructure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.ElementNameAndAttributeQualifier;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.mdsal.AbstractBaseSchemasTest;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps.ConfigNodeKey;
import org.opendaylight.netconf.client.mdsal.util.NormalizedDataUtil;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Commit;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.DiscardChanges;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.EditConfig;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Get;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.GetConfig;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.IetfNetconfData;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Lock;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.get.config.output.Data;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.CreateSubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.CreateSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.GetSchema;
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
import org.opendaylight.yangtools.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

class NetconfMessageTransformerTest extends AbstractBaseSchemasTest {

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

    @BeforeAll
    static void beforeClass() {
        PARTIAL_SCHEMA = BindingRuntimeHelpers.createEffectiveModel(NetconfState.class);
        SCHEMA = BindingRuntimeHelpers.createEffectiveModel(IetfNetconfData.class, NetconfState.class,
            NetconfConfigChange.class);
        ACTION_SCHEMA = YangParserTestUtils.parseYangResources(NetconfMessageTransformerTest.class,
            "/schemas/example-server-farm.yang","/schemas/example-server-farm-2.yang",
            "/schemas/conflicting-actions.yang", "/schemas/augmented-action.yang",
            "/schemas/rpcs-actions-outputs.yang");
    }

    @AfterAll
    static void afterClass() {
        PARTIAL_SCHEMA = null;
        SCHEMA = null;
        ACTION_SCHEMA = null;
    }

    @BeforeEach
    void setUp() {
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setIgnoreAttributeOrder(true);
        XMLUnit.setIgnoreComments(true);

        netconfMessageTransformer = getTransformer(SCHEMA);
        actionNetconfMessageTransformer = new NetconfMessageTransformer(DatabindContext.ofModel(ACTION_SCHEMA), true,
            BASE_SCHEMAS.baseSchemaForCapabilities(NetconfSessionPreferences.fromStrings(Set.of())));
    }

    @Test
    void testLockRequestBaseSchemaNotPresent() {
        final var transformer = getTransformer(PARTIAL_SCHEMA);
        final var netconfMessage = transformer.toRpcRequest(Lock.QNAME,
            NetconfBaseOps.getLockContent(NETCONF_CANDIDATE_NODEID));
        assertEquals("""
            <rpc xmlns="urn:ietf:params:xml:ns:netconf:base:1.0" message-id="m-0">
                <lock>
                    <target>
                        <candidate/>
                    </target>
                </lock>
            </rpc>
            """, XmlUtil.toString(netconfMessage.getDocument()));
    }

    @Test
    void testCreateSubscriberNotificationSchemaNotPresent() {
        final var transformer = new NetconfMessageTransformer(DatabindContext.ofModel(SCHEMA), true,
            BASE_SCHEMAS.baseSchemaForCapabilities(NetconfSessionPreferences.fromStrings(
                Set.of(CapabilityURN.NOTIFICATION))));
        var netconfMessage = transformer.toRpcRequest(CreateSubscription.QNAME, ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(CreateSubscriptionInput.QNAME))
            .build());
        assertEquals("""
            <rpc xmlns="urn:ietf:params:xml:ns:netconf:base:1.0" message-id="m-0">
                <create-subscription xmlns="urn:ietf:params:xml:ns:netconf:notification:1.0"/>
            </rpc>
            """, XmlUtil.toString(netconfMessage.getDocument()));
    }

    @Test
    void testLockSchemaRequest() throws Exception {
        final var transformer = getTransformer(PARTIAL_SCHEMA);
        final String result = "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"><ok/></rpc-reply>";

        transformer.toRpcResult(
            RpcResultBuilder.success(new NetconfMessage(XmlUtil.readXmlToDocument(result))).build(),
            Lock.QNAME);
    }

    @Test
    void testRpcEmptyBodyWithOutputDefinedSchemaResult() throws Exception {
        final String result = "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"><ok/></rpc-reply>";

        final var domRpcResult = actionNetconfMessageTransformer.toRpcResult(
            RpcResultBuilder.success(new NetconfMessage(XmlUtil.readXmlToDocument(result))).build(),
            RPC_WITH_OUTPUT_QNAME);
        assertNotNull(domRpcResult);
    }

    @Test
    void testRpcEmptyBodyWithoutOutputDefinedSchemaResult() throws Exception {
        final String result = "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"><ok/></rpc-reply>";

        final var domRpcResult = actionNetconfMessageTransformer.toRpcResult(
            RpcResultBuilder.success(new NetconfMessage(XmlUtil.readXmlToDocument(result))).build(),
            RPC_WITHOUT_OUTPUT_QNAME);
        assertNotNull(domRpcResult);
    }

    @Test
    void testDiscardChangesRequest() {
        final var netconfMessage = netconfMessageTransformer.toRpcRequest(DiscardChanges.QNAME, null);
        assertEquals("""
            <rpc xmlns="urn:ietf:params:xml:ns:netconf:base:1.0" message-id="m-0">
                <discard-changes/>
            </rpc>
            """, XmlUtil.toString(netconfMessage.getDocument()));
    }

    @Test
    void testGetSchemaRequest() throws Exception {
        final var netconfMessage = netconfMessageTransformer.toRpcRequest(GetSchema.QNAME,
                MonitoringSchemaSourceProvider.createGetSchemaRequest("module", Revision.of("2012-12-12")));
        assertSimilarXml(netconfMessage, """
            <rpc message-id="m-0" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
              <get-schema xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring">
                <format>yang</format>
                <identifier>module</identifier>
                <version>2012-12-12</version>
              </get-schema>
            </rpc>""");
    }

    @Test
    void testGetSchemaResponse() throws Exception {
        final var transformer = getTransformer(SCHEMA);
        final var response = new NetconfMessage(XmlUtil.readXmlToDocument("""
            <rpc-reply message-id="101" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
              <data xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring">
                <schema xmlns="http://www.w3.org/2001/XMLSchema">Random YANG SCHEMA</schema>
              </data>
            </rpc-reply>"""));
        final var compositeNodeRpcResult = transformer.toRpcResult(RpcResultBuilder.success(response).build(),
            GetSchema.QNAME);
        assertTrue(compositeNodeRpcResult.errors().isEmpty());
        assertNotNull(compositeNodeRpcResult.value());
        final var schemaContent = ((DOMSourceAnyxmlNode) compositeNodeRpcResult.value()
                .body().iterator().next()).body();
        assertEquals("""
            <data xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring">
                <schema xmlns="http://www.w3.org/2001/XMLSchema">Random YANG SCHEMA</schema>
            </data>
            """, XmlUtil.toString((Element) schemaContent.getNode()));
    }

    @Test
    void testGetConfigResponse() throws Exception {
        final var response = new NetconfMessage(XmlUtil.readXmlToDocument("""
            <rpc-reply message-id="101" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
              <data>
                 <netconf-state xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring">
                   <schemas>
                     <schema>
                       <identifier>module</identifier>
                       <version>2012-12-12</version>
                       <format xmlns:x="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring">x:yang</format>
                     </schema>
                   </schemas>
                 </netconf-state>
               </data>
             </rpc-reply>"""));

        final var transformer = getTransformer(SCHEMA);
        final var compositeNodeRpcResult = transformer.toRpcResult(RpcResultBuilder.success(response).build(),
            GetConfig.QNAME);
        assertTrue(compositeNodeRpcResult.errors().isEmpty());
        assertNotNull(compositeNodeRpcResult.value());
        assertEquals(NetconfMessageTransformUtil.NETCONF_OUTPUT_NODEID, compositeNodeRpcResult.value().name());

        final var values = MonitoringSchemaSourceProvider.createGetSchemaRequest("module", Revision.of("2012-12-12"))
            .body();

        final var keys = new HashMap<QName, Object>();
        for (var value : values) {
            keys.put(value.name().getNodeType(), value.body());
        }

        final var schemaNode = ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(Schema.QNAME, keys))
            .withValue(values)
            .build();

        final var data = (DOMSourceAnyxmlNode) compositeNodeRpcResult.value()
            .getChildByArg(new NodeIdentifier(Data.QNAME));

        final var nodeResult = NormalizedDataUtil.transformDOMSourceToNormalizedNode(SCHEMA, data.body());
        final var result = (ContainerNode) nodeResult.getResult().data();
        final var state = (ContainerNode) result.getChildByArg(new NodeIdentifier(NetconfState.QNAME));
        final var schemas = (ContainerNode) state.getChildByArg(new NodeIdentifier(Schemas.QNAME));
        final var schemaParent = (MapNode) schemas.getChildByArg(new NodeIdentifier(Schema.QNAME));
        assertEquals(1, schemaParent.body().size());

        assertEquals(schemaNode, schemaParent.body().iterator().next());
    }

    @Test
    void testGetConfigLeafRequest() throws Exception {
        final var filter = toFilterStructure(YangInstanceIdentifier.of(
            new NodeIdentifier(NetconfState.QNAME),
            new NodeIdentifier(Schemas.QNAME),
            new NodeIdentifier(Schema.QNAME),
            NodeIdentifierWithPredicates.of(Schema.QNAME),
            new NodeIdentifier(QName.create(Schemas.QNAME, "version"))), SCHEMA);

        final var source = NetconfBaseOps.getSourceNode(NETCONF_RUNNING_NODEID);

        final var netconfMessage = netconfMessageTransformer.toRpcRequest(GetConfig.QNAME,
                NetconfMessageTransformUtil.wrap(NETCONF_GET_CONFIG_NODEID, source, filter));

        assertSimilarXml(netconfMessage, """
            <rpc message-id="m-0" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
              <get-config>
                <filter type="subtree">
                  <netconf-state xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring">
                    <schemas>
                      <schema>
                        <version/>
                      </schema>
                    </schemas>
                  </netconf-state>
                </filter>
                <source>
                  <running/>
                </source>
              </get-config>
            </rpc>""");
    }

    @Test
    void testGetConfigRequest() throws Exception {
        final var filter = toFilterStructure(YangInstanceIdentifier.of(NetconfState.QNAME, Schemas.QNAME), SCHEMA);

        final var source = NetconfBaseOps.getSourceNode(NETCONF_RUNNING_NODEID);

        final var netconfMessage = netconfMessageTransformer.toRpcRequest(GetConfig.QNAME,
                NetconfMessageTransformUtil.wrap(NETCONF_GET_CONFIG_NODEID, source, filter));

        assertSimilarXml(netconfMessage, """
            <rpc message-id="m-0" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
              <get-config>
                <filter type="subtree">
                  <netconf-state xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring">
                    <schemas/>
                  </netconf-state>
                </filter>
                <source>
                 <running/>
                </source>
              </get-config>
            </rpc>""");
    }

    @Test
    void testEditConfigRequest() throws Exception {
        final var values = MonitoringSchemaSourceProvider.createGetSchemaRequest("module", Revision.of("2012-12-12"))
            .body();

        final var keys = new HashMap<QName, Object>();
        for (var value : values) {
            keys.put(value.name().getNodeType(), value.body());
        }

        final var schemaNode = ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(Schema.QNAME, keys))
            .withValue(values)
            .build();

        final var id = YangInstanceIdentifier.builder()
                .node(NetconfState.QNAME).node(Schemas.QNAME).node(Schema.QNAME)
                .nodeWithKey(Schema.QNAME, keys).build();
        final var editConfigStructure = createEditConfigStructure(BASE_SCHEMAS.baseSchemaForCapabilities(
            NetconfSessionPreferences.fromStrings(Set.of(
                CapabilityURN.CANDIDATE,
                "urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring?module=ietf-netconf-monitoring"
                    + "&revision=2010-10-04"))).modelContext(), id, Optional.empty(), Optional.ofNullable(schemaNode));

        final var target = NetconfBaseOps.getTargetNode(NETCONF_CANDIDATE_NODEID);

        final var wrap = NetconfMessageTransformUtil.wrap(NETCONF_EDIT_CONFIG_NODEID, editConfigStructure, target);
        final var netconfMessage = netconfMessageTransformer.toRpcRequest(EditConfig.QNAME, wrap);

        assertSimilarXml(netconfMessage, """
            <rpc message-id="m-0" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
              <edit-config>
                <target>
                  <candidate/>
                </target>
                <config>
                  <netconf-state xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring">
                    <schemas>
                      <schema>
                        <identifier>module</identifier>
                        <version>2012-12-12</version>
                        <format>yang</format>
                      </schema>
                    </schemas>
                  </netconf-state>
                </config>
              </edit-config>
            </rpc>""");
    }

    @Test
    void testSingleEditConfigRunningRequest() throws Exception {
        final var values = MonitoringSchemaSourceProvider.createGetSchemaRequest("module", Revision.of("2012-12-12"))
            .body();
        final var keys = new HashMap<QName, Object>();
        for (var value : values) {
            keys.put(value.name().getNodeType(), value.body());
        }

        final var schemaNode = ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(Schema.QNAME, keys))
            .withValue(values)
            .build();
        final var id = YangInstanceIdentifier.builder()
            .node(NetconfState.QNAME).node(Schemas.QNAME).node(Schema.QNAME)
            .nodeWithKey(Schema.QNAME, keys).build();

        final var elements = new LinkedHashMap<ConfigNodeKey, Optional<NormalizedNode>>();
        elements.put(new ConfigNodeKey(id, EffectiveOperation.DELETE), Optional.empty());
        elements.put(new ConfigNodeKey(id, EffectiveOperation.CREATE), Optional.of(schemaNode));
        elements.put(new ConfigNodeKey(id, null), Optional.of(schemaNode));

        final var editConfigStructure = createEditConfigAnyxml(BASE_SCHEMAS.baseSchemaForCapabilities(
            NetconfSessionPreferences.fromStrings(Set.of(
                CapabilityURN.WRITABLE_RUNNING,
                "urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring?module=ietf-netconf-monitoring"
                    + "&revision=2010-10-04"))).modelContext(), elements);
        final var editContentNode = ImmutableNodes.newChoiceBuilder()
            .withNodeIdentifier(EDIT_CONTENT_NODEID)
            .withChild(editConfigStructure)
            .build();

        final var target = NetconfBaseOps.getTargetNode(NETCONF_RUNNING_NODEID);
        final var wrap = NetconfMessageTransformUtil.wrap(NETCONF_EDIT_CONFIG_NODEID, editContentNode, target);
        final var netconfMessage = netconfMessageTransformer.toRpcRequest(EditConfig.QNAME, wrap);
        assertSimilarXml(netconfMessage, """
            <rpc message-id="m-0" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
              <edit-config>
                <target>
                  <running/>
                </target>
                <config>
                    <netconf-state xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring">
                        <schemas>
                            <schema xmlns:xc="urn:ietf:params:xml:ns:netconf:base:1.0" xc:operation="delete">
                                <identifier>module</identifier>
                                <format>yang</format>
                                <version>2012-12-12</version>
                            </schema>
                        </schemas>
                    </netconf-state>
                    <netconf-state xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring">
                        <schemas>
                            <schema xmlns:xc="urn:ietf:params:xml:ns:netconf:base:1.0" xc:operation="create">
                                <identifier>module</identifier>
                                <format>yang</format>
                                <version>2012-12-12</version>
                            </schema>
                        </schemas>
                    </netconf-state>
                    <netconf-state xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring">
                        <schemas>
                            <schema>
                                <identifier>module</identifier>
                                <format>yang</format>
                                <version>2012-12-12</version>
                            </schema>
                        </schemas>
                    </netconf-state>
                </config>
              </edit-config>
            </rpc>""");
    }

    private static void assertSimilarXml(final NetconfMessage netconfMessage, final String xmlContent)
            throws Exception {
        final Diff diff = XMLUnit.compareXML(netconfMessage.getDocument(), XmlUtil.readXmlToDocument(xmlContent));
        diff.overrideElementQualifier(new ElementNameAndAttributeQualifier());
        assertTrue(diff.similar(), diff.toString());
    }

    @Test
    void testGetRequest() throws Exception {
        final var capability = QName.create(Capabilities.QNAME, "capability");
        final var filter = toFilterStructure(YangInstanceIdentifier.of(
            new NodeIdentifier(NetconfState.QNAME),
            new NodeIdentifier(Capabilities.QNAME),
            new NodeIdentifier(capability),
            new NodeWithValue<>(capability, "a:b:c")), SCHEMA);

        final var netconfMessage = netconfMessageTransformer.toRpcRequest(Get.QNAME,
                NetconfMessageTransformUtil.wrap(NETCONF_GET_NODEID, filter));

        assertSimilarXml(netconfMessage, """
            <rpc message-id="m-0" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
              <get xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
                <filter type="subtree">
                  <netconf-state xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring">
                    <capabilities>
                      <capability>a:b:c</capability>
                    </capabilities>
                  </netconf-state>
                </filter>
              </get>
            </rpc>""");
    }

    @Test
    void testGetLeafList() throws Exception {
        final var filter = toFilterStructure(YangInstanceIdentifier.of(
            NetconfState.QNAME,
            Capabilities.QNAME,
            QName.create(Capabilities.QNAME, "capability")), SCHEMA);
        final var netconfMessage = netconfMessageTransformer.toRpcRequest(Get.QNAME,
                NetconfMessageTransformUtil.wrap(NETCONF_GET_NODEID, filter));

        assertSimilarXml(netconfMessage, """
            <rpc message-id="m-0" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
              <get>
                <filter type="subtree">
                  <netconf-state xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring">
                    <capabilities>
                      <capability/>
                    </capabilities>
                  </netconf-state>
                </filter>
              </get>
            </rpc>""");
    }

    @Test
    void testGetList() throws Exception {
        final var filter = toFilterStructure(YangInstanceIdentifier.of(
            NetconfState.QNAME,
            Datastores.QNAME,
            QName.create(Datastores.QNAME, "datastore")), SCHEMA);
        final var netconfMessage = netconfMessageTransformer.toRpcRequest(Get.QNAME,
                NetconfMessageTransformUtil.wrap(NETCONF_GET_NODEID, filter));

        assertSimilarXml(netconfMessage, """
            <rpc message-id="m-0" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
              <get>
                <filter type="subtree">
                  <netconf-state xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring">
                    <datastores>
                      <datastore/>
                    </datastores>
                  </netconf-state>
                </filter>
              </get>
            </rpc>""");
    }

    private static NetconfMessageTransformer getTransformer(final EffectiveModelContext schema) {
        return new NetconfMessageTransformer(DatabindContext.ofModel(schema), true,
            BASE_SCHEMAS.baseSchemaForCapabilities(NetconfSessionPreferences.fromStrings(Set.of())));
    }

    @Test
    void testCommitResponse() throws Exception {
        final var compositeNodeRpcResult = netconfMessageTransformer.toRpcResult(
            RpcResultBuilder.success(new NetconfMessage(XmlUtil.readXmlToDocument(
                "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"><ok/></rpc-reply>"))).build(),
            Commit.QNAME);
        assertTrue(compositeNodeRpcResult.errors().isEmpty());
        assertNull(compositeNodeRpcResult.value());
    }

    @Test
    void getActionsTest() {
        final var schemaPaths = Set.of(RESET_SERVER_PATH, START_DEVICE_PATH, ENABLE_INTERFACE_PATH, OPEN_BOXES_PATH,
            KILL_SERVER_APP_PATH, XYZZY_FOO_PATH, XYZZY_BAR_PATH, CHOICE_ACTION_PATH, DISABLE_INTERFACE_PATH,
            CHECK_WITH_OUTPUT_INTERFACE_PATH, CHECK_WITHOUT_OUTPUT_INTERFACE_PATH);

        var actions = NetconfMessageTransformer.getActions(ACTION_SCHEMA);
        assertEquals(schemaPaths.size(), actions.size());

        for (var path : schemaPaths) {
            assertNotNull(actions.get(path), "Action for " + path + " not found");
        }
    }

    @Test
    void toActionRequestListTopLevelTest() {
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
    void toActionRequestContainerTopLevelTest() {
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
    void toActionRequestContainerInContainerTest() {
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
    void toActionRequestListInContainerTest() {
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
    void toActionRequestListInContainerAugmentedIntoListTest() {
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
    void toActionRequestConflictingInListTest() {
        QName barInputQname = QName.create(BAR_QNAME, "bar");
        QName barIdQname = QName.create(BAR_QNAME, "bar-id");
        Byte barInput = 1;

        List<PathArgument> nodeIdentifiers = new ArrayList<>();
        nodeIdentifiers.add(NodeIdentifier.create(BAR_QNAME));
        nodeIdentifiers.add(NodeIdentifierWithPredicates.of(BAR_QNAME, barIdQname, "test"));

        DOMDataTreeIdentifier domDataTreeIdentifier = prepareDataTreeId(nodeIdentifiers);

        ContainerNode payload = ImmutableNodes.newContainerBuilder()
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
    void toActionRequestConflictingInContainerTest() {
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
    void toActionRequestChoiceTest() {
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
    void toAugmentedActionRequestListInContainerTest() {
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
    void toActionResultTest() throws Exception {
        var message = new NetconfMessage(XmlUtil.readXmlToDocument("""
            <rpc-reply message-id="101" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
              <reset-finished-at xmlns="urn:example:server-farm">now</reset-finished-at>
            </rpc-reply>"""));
        final var actionResult = actionNetconfMessageTransformer.toActionResult(RESET_SERVER_PATH, message);
        assertNotNull(actionResult);
        final var containerNode = actionResult.value();
        assertNotNull(containerNode);
        assertEquals("now", containerNode.body().iterator().next().body());
    }

    @Test
    void toActionEmptyBodyWithOutputDefinedResultTest() throws Exception {
        final var message = new NetconfMessage(XmlUtil.readXmlToDocument("""
            <rpc-reply message-id="101" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
              <ok/>
            </rpc-reply>"""));
        final var actionResult =
            actionNetconfMessageTransformer.toActionResult(CHECK_WITH_OUTPUT_INTERFACE_PATH, message);
        assertNotNull(actionResult);
        assertNull(actionResult.value());
    }

    @Test
    void toActionEmptyBodyWithoutOutputDefinedResultTest() throws Exception {
        final var message = new NetconfMessage(XmlUtil.readXmlToDocument("""
            <rpc-reply message-id="101" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
              <ok/>
            </rpc-reply>"""));
        final var actionResult =
            actionNetconfMessageTransformer.toActionResult(CHECK_WITHOUT_OUTPUT_INTERFACE_PATH, message);
        assertNotNull(actionResult);
        assertNull(actionResult.value());
    }

    @Test
    void getTwoNonOverlappingFieldsTest() throws Exception {
        // preparation of the fields
        final var parentYiid = YangInstanceIdentifier.of(NetconfState.QNAME);
        final var netconfStartTimeField = YangInstanceIdentifier.of(Statistics.QNAME,
                QName.create(Statistics.QNAME, "netconf-start-time"));
        final var datastoresField = YangInstanceIdentifier.of(Datastores.QNAME);

        // building filter structure and NETCONF message
        final var filterStructure = toFilterStructure(
            List.of(FieldsFilter.of(parentYiid, List.of(netconfStartTimeField, datastoresField))), SCHEMA);
        final var netconfMessage = netconfMessageTransformer.toRpcRequest(Get.QNAME,
                NetconfMessageTransformUtil.wrap(NETCONF_GET_NODEID, filterStructure));

        // testing
        assertSimilarXml(netconfMessage, """
            <rpc message-id="m-0" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
              <get>
                <filter type="subtree">
                  <netconf-state xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring">
                    <statistics>
                      <netconf-start-time/>
                    </statistics>
                    <datastores/>
                  </netconf-state>
                </filter>
              </get>
            </rpc>""");
    }

    @Test
    void getOverlappingFieldsTest() throws Exception {
        // preparation of the fields
        final var parentYiid = YangInstanceIdentifier.of(NetconfState.QNAME);
        final var capabilitiesField = YangInstanceIdentifier.of(Capabilities.QNAME);
        final var capabilityField = YangInstanceIdentifier.of(Capabilities.QNAME,
                QName.create(Capabilities.QNAME, "capability"));
        final var datastoreField = YangInstanceIdentifier.of(Datastores.QNAME);
        final var locksFields = YangInstanceIdentifier.of(
            new NodeIdentifier(Datastores.QNAME),
            new NodeIdentifier(Datastore.QNAME),
            // Note: acts as 'select all'
            NodeIdentifierWithPredicates.of(Datastore.QNAME),
            new NodeIdentifier(Locks.QNAME));

        // building filter structure and NETCONF message
        final var filterStructure = toFilterStructure(
                List.of(FieldsFilter.of(parentYiid,
                    List.of(capabilitiesField, capabilityField, datastoreField, locksFields))),
                SCHEMA);
        final var netconfMessage = netconfMessageTransformer.toRpcRequest(Get.QNAME,
                NetconfMessageTransformUtil.wrap(NETCONF_GET_NODEID, filterStructure));

        // testing
        assertSimilarXml(netconfMessage, """
            <rpc message-id="m-0" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
              <get>
                <filter type="subtree">
                  <netconf-state xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring">
                    <capabilities/>
                    <datastores/>
                  </netconf-state>
                </filter>
              </get>
            </rpc>""");
    }

    @Test
    void getOverlappingFieldsWithEmptyFieldTest() throws Exception {
        // preparation of the fields
        final var parentYiid = YangInstanceIdentifier.of(NetconfState.QNAME);
        final var capabilitiesField = YangInstanceIdentifier.of(Capabilities.QNAME);

        // building filter structure and NETCONF message
        final var filterStructure = toFilterStructure(
                List.of(FieldsFilter.of(parentYiid, List.of(capabilitiesField, YangInstanceIdentifier.of()))),
                SCHEMA);
        final var netconfMessage = netconfMessageTransformer.toRpcRequest(Get.QNAME,
                NetconfMessageTransformUtil.wrap(NETCONF_GET_NODEID, filterStructure));

        // testing
        assertSimilarXml(netconfMessage, """
            <rpc message-id="m-0" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
            <get>
            <filter type="subtree">
            <netconf-state xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring"/>
            </filter>
            </get>
            </rpc>""");
    }

    @Test
    void getSpecificFieldsUnderListTest() throws Exception {
        // preparation of the fields
        final var parentYiid = YangInstanceIdentifier.of(
            new NodeIdentifier(NetconfState.QNAME),
            new NodeIdentifier(Schemas.QNAME),
            new NodeIdentifier(Schema.QNAME),
            NodeIdentifierWithPredicates.of(Schema.QNAME));
        final var versionField = YangInstanceIdentifier.of(
                QName.create(Schema.QNAME, "version"));
        final var identifierField = YangInstanceIdentifier.of(
                QName.create(Schema.QNAME, "namespace"));

        // building filter structure and NETCONF message
        final var filterStructure = toFilterStructure(
            List.of(FieldsFilter.of(parentYiid, List.of(versionField, identifierField))), SCHEMA);
        final var netconfMessage = netconfMessageTransformer.toRpcRequest(Get.QNAME,
                NetconfMessageTransformUtil.wrap(NETCONF_GET_NODEID, filterStructure));

        // testing
        assertSimilarXml(netconfMessage, """
            <rpc message-id="m-0" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
              <get>
                <filter type="subtree">
                  <netconf-state xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring">
                    <schemas>
                      <schema>
                        <version/>
                        <namespace/>
                        <identifier/>
                        <format/>
                      </schema>
                    </schemas>
                  </netconf-state>
                </filter>
              </get>
            </rpc>""");
    }

    @Test
    void getSpecificFieldsUnderMultipleLists() throws Exception {
        // preparation of the fields
        final var parentYiid = YangInstanceIdentifier.of(NetconfState.QNAME, Datastores.QNAME);
        final var partialLockYiid = YangInstanceIdentifier.of(
            new NodeIdentifier(Datastore.QNAME),
            NodeIdentifierWithPredicates.of(Datastore.QNAME),
            new NodeIdentifier(Locks.QNAME),
            new NodeIdentifier(QName.create(Locks.QNAME, "lock-type")),
            new NodeIdentifier(PartialLock.QNAME),
            NodeIdentifierWithPredicates.of(PartialLock.QNAME));
        final var lockedTimeField = partialLockYiid.node(QName.create(Locks.QNAME, "locked-time"));
        final var lockedBySessionField = partialLockYiid.node(QName.create(Locks.QNAME, "locked-by-session"));

        // building filter structure and NETCONF message
        final var filterStructure = toFilterStructure(
            List.of(FieldsFilter.of(parentYiid, List.of(lockedTimeField, lockedBySessionField))),
            SCHEMA);
        final var netconfMessage = netconfMessageTransformer.toRpcRequest(Get.QNAME,
                NetconfMessageTransformUtil.wrap(NETCONF_GET_NODEID, filterStructure));

        // testing
        assertSimilarXml(netconfMessage, """
            <rpc message-id="m-0" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
              <get>
                <filter type="subtree">
                  <netconf-state xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring">
                    <datastores>
                      <datastore>
                        <locks>
                          <partial-lock>
                            <locked-time/>
                            <locked-by-session/>
                            <lock-id/>
                          </partial-lock>
                        </locks>
                        <name/>
                      </datastore>
                    </datastores>
                  </netconf-state>
                </filter>
              </get>
            </rpc>""");
    }

    @Test
    void getWholeListsUsingFieldsTest() throws Exception {
        // preparation of the fields
        final var parentYiid = YangInstanceIdentifier.of(NetconfState.QNAME);
        final var datastoreListField = YangInstanceIdentifier.of(
            new NodeIdentifier(Datastores.QNAME),
            new NodeIdentifier(Datastore.QNAME),
            NodeIdentifierWithPredicates.of(Datastore.QNAME));
        final var sessionListField = YangInstanceIdentifier.of(
            new NodeIdentifier(Sessions.QNAME),
            new NodeIdentifier(Session.QNAME),
            NodeIdentifierWithPredicates.of(Session.QNAME));

        // building filter structure and NETCONF message
        final var filterStructure = toFilterStructure(
                List.of(FieldsFilter.of(parentYiid, List.of(datastoreListField, sessionListField))), SCHEMA);
        final var netconfMessage = netconfMessageTransformer.toRpcRequest(Get.QNAME,
                NetconfMessageTransformUtil.wrap(NETCONF_GET_NODEID, filterStructure));

        // testing
        assertSimilarXml(netconfMessage, """
            <rpc message-id="m-0" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
              <get>
                <filter type="subtree">
                  <netconf-state xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring">
                    <datastores>
                      <datastore/>
                    </datastores>
                    <sessions>
                      <session/>
                    </sessions>
                  </netconf-state>
                </filter>
              </get>
            </rpc>""");
    }

    @Test
    void getSpecificListEntriesWithSpecificFieldsTest() throws Exception {
        // preparation of the fields
        final var parentYiid = YangInstanceIdentifier.of(NetconfState.QNAME, Sessions.QNAME);
        final var sessionId = QName.create(Session.QNAME, "session-id");
        final var session1Field = YangInstanceIdentifier.of(
            new NodeIdentifier(Session.QNAME),
            NodeIdentifierWithPredicates.of(Session.QNAME, sessionId, 1));
        final var session2TransportField = YangInstanceIdentifier.of(
            new NodeIdentifier(Session.QNAME),
            NodeIdentifierWithPredicates.of(Session.QNAME, sessionId, 2),
            new NodeIdentifier(QName.create(Session.QNAME, "transport")));

        // building filter structure and NETCONF message
        final var filterStructure = toFilterStructure(
                List.of(FieldsFilter.of(parentYiid, List.of(session1Field, session2TransportField))), SCHEMA);
        final var netconfMessage = netconfMessageTransformer.toRpcRequest(Get.QNAME,
                NetconfMessageTransformUtil.wrap(NETCONF_GET_NODEID, filterStructure));

        // testing
        assertSimilarXml(netconfMessage, """
            <rpc message-id="m-0" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
              <get>
                <filter type="subtree">
                  <netconf-state xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring">
                    <sessions>
                      <session>
                        <session-id>1</session-id>
                      </session>
                      <session>
                        <session-id>2</session-id>
                        <transport/>
                      </session>
                    </sessions>
                  </netconf-state>
                </filter>
              </get>
            </rpc>""");
    }

    @Test
    // Proof that YANGTOOLS-1362 works on DOM level
    void testConfigChangeToNotification() throws Exception {
        final var message = new NetconfMessage(XmlUtil.readXmlToDocument(
            """
                <notification xmlns="urn:ietf:params:xml:ns:netconf:notification:1.0">
                 <eventTime>2021-11-11T11:26:16Z</eventTime>\s
                  <netconf-config-change xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-notifications">
                     <changed-by>\s
                       <username>root</username>\s
                       <session-id>3</session-id>\s
                     </changed-by>\s
                     <datastore>running</datastore>\s
                     <edit>\s
                        <target xmlns:ncm="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring">/ncm:netconf-state\
                /ncm:datastores/ncm:datastore[ncm:name='running']</target>
                        <operation>replace</operation>\s
                     </edit>\s
                  </netconf-config-change>\s
                </notification>"""));

        final var change = netconfMessageTransformer.toNotification(message).getBody();
        final var editList = change.getChildByArg(new NodeIdentifier(Edit.QNAME));
        final var unkeyedListNode = assertInstanceOf(UnkeyedListNode.class, editList);
        final var edits = unkeyedListNode.body();
        assertEquals(1, edits.size());
        final var edit = edits.iterator().next();
        final var target = edit.getChildByArg(new NodeIdentifier(QName.create(Edit.QNAME, "target"))).body();
        final var identifier = assertInstanceOf(YangInstanceIdentifier.class, target);

        final var args = identifier.getPathArguments();
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
        checkNode(baseRpc, "rpc", "rpc", "urn:ietf:params:xml:ns:netconf:base:1.0");
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
                DOMDataTreeIdentifier.of(org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION,
                        yangInstanceIdentifier);
        return domDataTreeIdentifier;
    }

    private static ContainerNode initInputAction(final QName qname, final String value) {
        return ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(QName.create(qname, "input")))
            .withChild(ImmutableNodes.leafNode(qname, value))
            .build();
    }

    private static ContainerNode initEmptyInputAction(final QName qname) {
        return ImmutableNodes.newContainerBuilder()
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
