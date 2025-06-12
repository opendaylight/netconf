/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.mdsal.AbstractTestModelTest;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.spi.NetconfDeviceRpc;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.IetfNetconfData;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.get.config.output.Data;
import org.opendaylight.yangtools.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.xml.sax.SAXException;

@ExtendWith(MockitoExtension.class)
class NetconfBaseOpsTest extends AbstractTestModelTest {
    private static final QNameModule TEST_MODULE = QNameModule.ofRevision("test:namespace", "2013-07-22");

    private static final QName CONTAINER_C_QNAME = QName.create(TEST_MODULE, "c");
    private static final NodeIdentifier CONTAINER_C_NID = NodeIdentifier.create(CONTAINER_C_QNAME);
    private static final QName LEAF_A_QNAME = QName.create(TEST_MODULE, "a");
    private static final NodeIdentifier LEAF_A_NID = NodeIdentifier.create(LEAF_A_QNAME);
    private static final QName LEAF_B_QNAME = QName.create(TEST_MODULE, "b");
    private static final NodeIdentifier LEAF_B_NID = NodeIdentifier.create(LEAF_B_QNAME);
    private static final QName CONTAINER_D_QNAME = QName.create(TEST_MODULE, "d");
    private static final NodeIdentifier CONTAINER_D_NID = NodeIdentifier.create(CONTAINER_D_QNAME);
    private static final QName LEAF_X_QNAME = QName.create(TEST_MODULE, "x");
    private static final NodeIdentifier LEAF_X_NID = NodeIdentifier.create(LEAF_X_QNAME);

    private static final QName CONTAINER_E_QNAME = QName.create(TEST_MODULE, "e");
    private static final NodeIdentifier CONTAINER_E_NID = NodeIdentifier.create(CONTAINER_E_QNAME);
    private static final QName LEAF_Z_QNAME = QName.create(TEST_MODULE, "z");
    private static final NodeIdentifier LEAF_Z_NID = NodeIdentifier.create(LEAF_Z_QNAME);
    private static final NetconfMessage NETCONF_DATA_MESSAGE;

    private static EffectiveModelContext schemaContext;

    static {
        final var dataStream = NetconfBaseOpsTest.class.getResourceAsStream("/netconfMessages/rpc-reply_get.xml");
        try {
            NETCONF_DATA_MESSAGE = new NetconfMessage(XmlUtil.readXmlToDocument(dataStream));
        } catch (SAXException | IOException e) {
            throw new ExceptionInInitializerError(e);
        }
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setIgnoreComments(true);
    }

    @Mock
    private RemoteDeviceCommunicator listener;
    private NetconfRpcFutureCallback callback;
    private NetconfBaseOps baseOps;

    @BeforeAll
    static void beforeClass() {
        schemaContext = BindingRuntimeHelpers.createEffectiveModel(IetfNetconfData.class);
    }

    @BeforeEach
    void setUp() {
        final var rpc = new NetconfDeviceRpc(schemaContext, listener,
            new NetconfMessageTransformer(DatabindContext.ofModel(schemaContext),
            true, BASE_SCHEMAS.baseSchemaForCapabilities(NetconfSessionPreferences.fromStrings(Set.of()))));
        callback = new NetconfRpcFutureCallback("prefix",
            new RemoteDeviceId("device-1", InetSocketAddress.createUnresolved("localhost", 17830)));
        baseOps = new NetconfBaseOps(TEST_DATABIND, rpc);
    }

    @Test
    void testLock() {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        baseOps.lock(callback, NetconfMessageTransformUtil.NETCONF_CANDIDATE_NODEID);
        verifyMessageSent("lock");
    }

    @Test
    void testLockCandidate() {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        baseOps.lockCandidate(callback);
        verifyMessageSent("lock");
    }

    @Test
    void testUnlock() {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        baseOps.unlock(callback, NetconfMessageTransformUtil.NETCONF_CANDIDATE_NODEID);
        verifyMessageSent("unlock");
    }

    @Test
    void testUnlockCandidate() {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        baseOps.unlockCandidate(callback);
        verifyMessageSent("unlock");
    }

    @Test
    void testLockRunning() {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        baseOps.lockRunning(callback);
        verifyMessageSent("lock-running");
    }

    @Test
    void testUnlockRunning() {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        baseOps.unlockRunning(callback);
        verifyMessageSent("unlock-running");
    }

    @Test
    void testDiscardChanges() {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        baseOps.discardChanges(callback);
        verifyMessageSent("discardChanges");
    }

    @Test
    void testCommit() {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        baseOps.commit(callback);
        verifyMessageSent("commit");
    }

    @Test
    void testValidateCandidate() {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        baseOps.validateCandidate(callback);
        verifyMessageSent("validate");
    }

    @Test
    void testValidateRunning() {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        baseOps.validateRunning(callback);
        verifyMessageSent("validate-running");
    }


    @Test
    void testCopyConfig() {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        baseOps.copyConfig(callback, NetconfMessageTransformUtil.NETCONF_RUNNING_NODEID,
                NetconfMessageTransformUtil.NETCONF_CANDIDATE_NODEID);
        verifyMessageSent("copy-config");
    }

    @Test
    void testCopyRunningToCandidate() {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        baseOps.copyRunningToCandidate(callback);
        verifyMessageSent("copy-config");
    }

    @Test
    void testGetConfigRunningData() throws Exception {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        final var dataOpt = baseOps.getConfigRunningData(callback, Optional.of(YangInstanceIdentifier.of())).get();
        assertTrue(dataOpt.isPresent());
        assertEquals(Data.QNAME, dataOpt.orElseThrow().name().getNodeType());
    }

    @Test
    void testGetData() throws Exception {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        final var dataOpt = baseOps.getData(callback, Optional.of(YangInstanceIdentifier.of())).get();
        assertTrue(dataOpt.isPresent());
        assertEquals(Data.QNAME, dataOpt.orElseThrow().name().getNodeType());
    }

    @Test
    void testGetConfigRunning() {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        baseOps.getConfigRunning(callback, Optional.empty());
        verifyMessageSent("getConfig");
    }

    @Test
    void testGetConfigCandidate() {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        baseOps.getConfigCandidate(callback, Optional.empty());
        verifyMessageSent("getConfig_candidate");
    }

    @Test
    void testGetConfigCandidateWithFilter() {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        baseOps.getConfigCandidate(callback, Optional.of(YangInstanceIdentifier.of(CONTAINER_C_QNAME)));
        verifyMessageSent("getConfig_candidate-filter");
    }

    @Test
    void testGet() {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        baseOps.get(callback, Optional.empty());
        verifyMessageSent("get");
    }

    @Test
    void testEditConfigCandidate() {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        baseOps.editConfigCandidate(callback, baseOps.createEditConfigStructure(
            Optional.of(ImmutableNodes.leafNode(LEAF_A_NID, "leaf-value")),
            Optional.of(EffectiveOperation.REPLACE), YangInstanceIdentifier.builder()
            .node(CONTAINER_C_QNAME)
            .node(LEAF_A_NID)
            .build()), true);
        verifyMessageSent("edit-config-test-module");
    }

    @Test
    void testDeleteContainerNodeCandidate() {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        baseOps.editConfigCandidate(callback, baseOps.createEditConfigStructure(Optional.empty(),
            Optional.of(EffectiveOperation.DELETE), YangInstanceIdentifier.of(CONTAINER_C_QNAME)), true);
        verifyMessageSent("edit-config-delete-container-node-candidate");
    }

    @Test
    void testDeleteLeafNodeCandidate() {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        baseOps.editConfigCandidate(callback, baseOps.createEditConfigStructure(Optional.empty(),
            Optional.of(EffectiveOperation.DELETE),
            YangInstanceIdentifier.builder().node(CONTAINER_C_QNAME).node(LEAF_A_NID).build()), true);
        verifyMessageSent("edit-config-delete-leaf-node-candidate");
    }

    @Test
    void testEditConfigRunning() {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        baseOps.editConfigRunning(callback, baseOps.createEditConfigStructure(
            Optional.of(ImmutableNodes.leafNode(LEAF_A_NID, "leaf-value")),
            Optional.of(EffectiveOperation.REPLACE),
            YangInstanceIdentifier.builder().node(CONTAINER_C_NID).node(LEAF_A_NID).build()),
            EffectiveOperation.MERGE, true);
        verifyMessageSent("edit-config-test-module-running");
    }

    @Test
    void testGetWithFields() throws Exception {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        final YangInstanceIdentifier path = YangInstanceIdentifier.of(CONTAINER_C_NID);
        final YangInstanceIdentifier leafAField = YangInstanceIdentifier.of(LEAF_A_NID);
        final YangInstanceIdentifier leafBField = YangInstanceIdentifier.of(LEAF_B_NID);

        baseOps.getData(callback, Optional.of(path), List.of(leafAField, leafBField)).get();
        verify(listener).sendRequest(msg("/netconfMessages/get-fields-request.xml"));
    }

    @Test
    void testGetConfigWithFields() throws Exception {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        final YangInstanceIdentifier path = YangInstanceIdentifier.of(CONTAINER_C_NID);
        final YangInstanceIdentifier leafAField = YangInstanceIdentifier.of(LEAF_A_NID);
        final YangInstanceIdentifier leafBField = YangInstanceIdentifier.of(LEAF_B_NID);

        baseOps.getConfigRunningData(callback, Optional.of(path), List.of(leafAField, leafBField)).get();
        verify(listener).sendRequest(msg("/netconfMessages/get-config-fields-request.xml"));
    }

    @Test
    void testGetDataWithoutFields() {
        assertThrows(ExecutionException.class, () -> baseOps.getData(callback,
                Optional.of(YangInstanceIdentifier.of()), List.of()).get());
    }

    @Test
    void getConfigRunningDataWithoutFields() {
        assertThrows(ExecutionException.class, () -> baseOps.getConfigRunningData(callback,
                Optional.of(YangInstanceIdentifier.of()), List.of()).get());
    }

    @Test
    void testGetWithFieldsAndEmptyParentPath() throws Exception {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        final YangInstanceIdentifier leafAField = YangInstanceIdentifier.of(CONTAINER_C_NID, LEAF_A_NID);
        final YangInstanceIdentifier leafXField = YangInstanceIdentifier.of(
                CONTAINER_C_NID, CONTAINER_D_NID, LEAF_X_NID);
        final YangInstanceIdentifier leafZField = YangInstanceIdentifier.of(CONTAINER_E_NID, LEAF_Z_NID);

        baseOps.getData(callback, Optional.of(YangInstanceIdentifier.of()),
                List.of(leafAField, leafXField, leafZField)).get();
        verify(listener).sendRequest(msg("/netconfMessages/get-with-multiple-subtrees.xml"));
    }

    @Test
    void testGetConfigWithFieldsAndEmptyParentPath() throws Exception {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        final YangInstanceIdentifier leafAField = YangInstanceIdentifier.of(CONTAINER_C_NID, LEAF_A_NID);
        final YangInstanceIdentifier leafXField = YangInstanceIdentifier.of(
                CONTAINER_C_NID, CONTAINER_D_NID, LEAF_X_NID);
        final YangInstanceIdentifier leafZField = YangInstanceIdentifier.of(CONTAINER_E_NID, LEAF_Z_NID);

        baseOps.getConfigRunningData(callback, Optional.of(YangInstanceIdentifier.of()),
                List.of(leafAField, leafXField, leafZField)).get();
        verify(listener).sendRequest(msg("/netconfMessages/get-config-with-multiple-subtrees.xml"));
    }

    @Test
    void testGetWithRootFieldsAndEmptyParentPath() throws Exception {
        when(listener.sendRequest(any())).thenReturn(RpcResultBuilder.success(NETCONF_DATA_MESSAGE).buildFuture());
        final YangInstanceIdentifier contCField = YangInstanceIdentifier.of(CONTAINER_C_NID);
        final YangInstanceIdentifier contDField = YangInstanceIdentifier.of(CONTAINER_E_NID);

        baseOps.getData(callback, Optional.of(YangInstanceIdentifier.of()), List.of(contCField, contDField)).get();
        verify(listener).sendRequest(msg("/netconfMessages/get-with-multiple-root-subtrees.xml"));
    }

    private void verifyMessageSent(final String fileName) {
        final String path = "/netconfMessages/" + fileName + ".xml";
        verify(listener).sendRequest(msg(path));
    }

    private static NetconfMessage msg(final String name) {
        final InputStream stream = NetconfBaseOpsTest.class.getResourceAsStream(name);
        try {
            return argThat(new NetconfMessageMatcher(XmlUtil.readXmlToDocument(stream)));
        } catch (SAXException | IOException e) {
            throw new IllegalStateException("Failed to read xml file " + name, e);
        }
    }

    private static class NetconfMessageMatcher implements ArgumentMatcher<NetconfMessage> {

        private final Document expected;

        NetconfMessageMatcher(final Document expected) {
            this.expected = removeAttrs(expected);
        }

        @Override
        public boolean matches(final NetconfMessage message) {
            final Document actualDoc = removeAttrs(message.getDocument());
            actualDoc.normalizeDocument();
            expected.normalizeDocument();
            final Diff diff = XMLUnit.compareXML(expected, actualDoc);
            return diff.similar();
        }

        private static Document removeAttrs(final Document input) {
            final Document copy = XmlUtil.newDocument();
            copy.appendChild(copy.importNode(input.getDocumentElement(), true));
            final Element element = copy.getDocumentElement();
            final List<String> attrNames = new ArrayList<>();
            final NamedNodeMap attributes = element.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                final String nodeName = attributes.item(i).getNodeName();
                if ("xmlns".equals(nodeName)) {
                    continue;
                }
                attrNames.add(nodeName);
            }
            attrNames.forEach(element::removeAttribute);
            return copy;
        }
    }
}
