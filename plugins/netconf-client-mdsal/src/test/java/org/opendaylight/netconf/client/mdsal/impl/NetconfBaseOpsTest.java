/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.mdsal.AbstractTestModelTest;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.spi.NetconfDeviceRpc;
import org.opendaylight.netconf.common.mdsal.NormalizedDataUtil;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.xml.sax.SAXException;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfBaseOpsTest extends AbstractTestModelTest {
    private static final QNameModule TEST_MODULE = QNameModule.create(
            XMLNamespace.of("test:namespace"), Revision.of("2013-07-22"));

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

    static {
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setIgnoreComments(true);
    }

    @Mock
    private RemoteDeviceCommunicator listener;
    private NetconfRpcFutureCallback callback;
    private NetconfBaseOps baseOps;

    @Before
    public void setUp() throws Exception {
        final InputStream okStream = getClass().getResourceAsStream("/netconfMessages/rpc-reply_ok.xml");
        final InputStream dataStream = getClass().getResourceAsStream("/netconfMessages/rpc-reply_get.xml");
        final NetconfMessage ok = new NetconfMessage(XmlUtil.readXmlToDocument(okStream));
        final NetconfMessage data = new NetconfMessage(XmlUtil.readXmlToDocument(dataStream));
        when(listener.sendRequest(any(), eq(NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME)))
                .thenReturn(RpcResultBuilder.success(data).buildFuture());
        when(listener.sendRequest(any(), eq(NetconfMessageTransformUtil.NETCONF_GET_QNAME)))
                .thenReturn(RpcResultBuilder.success(data).buildFuture());
        when(listener.sendRequest(any(), eq(NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME)))
                .thenReturn(RpcResultBuilder.success(ok).buildFuture());
        when(listener.sendRequest(any(), eq(NetconfMessageTransformUtil.NETCONF_COPY_CONFIG_QNAME)))
                .thenReturn(RpcResultBuilder.success(ok).buildFuture());
        when(listener.sendRequest(any(), eq(NetconfMessageTransformUtil.NETCONF_DISCARD_CHANGES_QNAME)))
                .thenReturn(RpcResultBuilder.success(ok).buildFuture());
        when(listener.sendRequest(any(), eq(NetconfMessageTransformUtil.NETCONF_VALIDATE_QNAME)))
                .thenReturn(RpcResultBuilder.success(ok).buildFuture());
        when(listener.sendRequest(any(), eq(NetconfMessageTransformUtil.NETCONF_LOCK_QNAME)))
                .thenReturn(RpcResultBuilder.success(ok).buildFuture());
        when(listener.sendRequest(any(), eq(NetconfMessageTransformUtil.NETCONF_UNLOCK_QNAME)))
                .thenReturn(RpcResultBuilder.success(ok).buildFuture());
        when(listener.sendRequest(any(), eq(NetconfMessageTransformUtil.NETCONF_COMMIT_QNAME)))
                .thenReturn(RpcResultBuilder.success(ok).buildFuture());
        final var rpc = new NetconfDeviceRpc(SCHEMA_CONTEXT, listener, new NetconfMessageTransformer(
            MountPointContext.of(SCHEMA_CONTEXT), true, BASE_SCHEMAS.getBaseSchema()));
        callback = new NetconfRpcFutureCallback("prefix",
            new RemoteDeviceId("device-1", InetSocketAddress.createUnresolved("localhost", 17830)));
        baseOps = new NetconfBaseOps(rpc, MountPointContext.of(SCHEMA_CONTEXT));
    }

    @Test
    public void testLock() throws Exception {
        baseOps.lock(callback, NetconfMessageTransformUtil.NETCONF_CANDIDATE_NODEID);
        verifyMessageSent("lock", NetconfMessageTransformUtil.NETCONF_LOCK_QNAME);
    }

    @Test
    public void testLockCandidate() throws Exception {
        baseOps.lockCandidate(callback);
        verifyMessageSent("lock", NetconfMessageTransformUtil.NETCONF_LOCK_QNAME);
    }

    @Test
    public void testUnlock() throws Exception {
        baseOps.unlock(callback, NetconfMessageTransformUtil.NETCONF_CANDIDATE_NODEID);
        verifyMessageSent("unlock", NetconfMessageTransformUtil.NETCONF_UNLOCK_QNAME);
    }

    @Test
    public void testUnlockCandidate() throws Exception {
        baseOps.unlockCandidate(callback);
        verifyMessageSent("unlock", NetconfMessageTransformUtil.NETCONF_UNLOCK_QNAME);
    }

    @Test
    public void testLockRunning() throws Exception {
        baseOps.lockRunning(callback);
        verifyMessageSent("lock-running", NetconfMessageTransformUtil.NETCONF_LOCK_QNAME);
    }

    @Test
    public void testUnlockRunning() throws Exception {
        baseOps.unlockRunning(callback);
        verifyMessageSent("unlock-running", NetconfMessageTransformUtil.NETCONF_UNLOCK_QNAME);
    }

    @Test
    public void testDiscardChanges() throws Exception {
        baseOps.discardChanges(callback);
        verifyMessageSent("discardChanges", NetconfMessageTransformUtil.NETCONF_DISCARD_CHANGES_QNAME);
    }

    @Test
    public void testCommit() throws Exception {
        baseOps.commit(callback);
        verifyMessageSent("commit", NetconfMessageTransformUtil.NETCONF_COMMIT_QNAME);
    }

    @Test
    public void testValidateCandidate() throws Exception {
        baseOps.validateCandidate(callback);
        verifyMessageSent("validate", NetconfMessageTransformUtil.NETCONF_VALIDATE_QNAME);
    }

    @Test
    public void testValidateRunning() throws Exception {
        baseOps.validateRunning(callback);
        verifyMessageSent("validate-running", NetconfMessageTransformUtil.NETCONF_VALIDATE_QNAME);
    }


    @Test
    public void testCopyConfig() throws Exception {
        baseOps.copyConfig(callback, NetconfMessageTransformUtil.NETCONF_RUNNING_NODEID,
                NetconfMessageTransformUtil.NETCONF_CANDIDATE_NODEID);
        verifyMessageSent("copy-config", NetconfMessageTransformUtil.NETCONF_COPY_CONFIG_QNAME);
    }

    @Test
    public void testCopyRunningToCandidate() throws Exception {
        baseOps.copyRunningToCandidate(callback);
        verifyMessageSent("copy-config", NetconfMessageTransformUtil.NETCONF_COPY_CONFIG_QNAME);
    }

    @Test
    public void testGetConfigRunningData() throws Exception {
        final var dataOpt = baseOps.getConfigRunningData(callback, Optional.of(YangInstanceIdentifier.of())).get();
        assertTrue(dataOpt.isPresent());
        assertEquals(NormalizedDataUtil.NETCONF_DATA_QNAME, dataOpt.orElseThrow().name().getNodeType());
    }

    @Test
    public void testGetData() throws Exception {
        final var dataOpt = baseOps.getData(callback, Optional.of(YangInstanceIdentifier.of())).get();
        assertTrue(dataOpt.isPresent());
        assertEquals(NormalizedDataUtil.NETCONF_DATA_QNAME, dataOpt.orElseThrow().name().getNodeType());
    }

    @Test
    public void testGetConfigRunning() throws Exception {
        baseOps.getConfigRunning(callback, Optional.empty());
        verifyMessageSent("getConfig", NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME);
    }

    @Test
    public void testGetConfigCandidate() throws Exception {
        baseOps.getConfigCandidate(callback, Optional.empty());
        verifyMessageSent("getConfig_candidate", NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME);
    }

    @Test
    public void testGetConfigCandidateWithFilter() throws Exception {
        baseOps.getConfigCandidate(callback, Optional.of(YangInstanceIdentifier.of(CONTAINER_C_QNAME)));
        verifyMessageSent("getConfig_candidate-filter", NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME);
    }

    @Test
    public void testGet() throws Exception {
        baseOps.get(callback, Optional.empty());
        verifyMessageSent("get", NetconfMessageTransformUtil.NETCONF_GET_QNAME);
    }

    @Test
    public void testEditConfigCandidate() throws Exception {
        baseOps.editConfigCandidate(callback, baseOps.createEditConfigStructure(
            Optional.of(ImmutableNodes.leafNode(LEAF_A_NID, "leaf-value")),
            Optional.of(EffectiveOperation.REPLACE), YangInstanceIdentifier.builder()
            .node(CONTAINER_C_QNAME)
            .node(LEAF_A_NID)
            .build()), true);
        verifyMessageSent("edit-config-test-module", NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME);
    }

    @Test
    public void testDeleteContainerNodeCandidate() throws Exception {
        baseOps.editConfigCandidate(callback, baseOps.createEditConfigStructure(Optional.empty(),
            Optional.of(EffectiveOperation.DELETE), YangInstanceIdentifier.of(CONTAINER_C_QNAME)), true);
        verifyMessageSent("edit-config-delete-container-node-candidate",
                NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME);
    }

    @Test
    public void testDeleteLeafNodeCandidate() throws Exception {
        baseOps.editConfigCandidate(callback, baseOps.createEditConfigStructure(Optional.empty(),
            Optional.of(EffectiveOperation.DELETE),
            YangInstanceIdentifier.builder().node(CONTAINER_C_QNAME).node(LEAF_A_NID).build()), true);
        verifyMessageSent("edit-config-delete-leaf-node-candidate",
                NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME);
    }

    @Test
    public void testEditConfigRunning() throws Exception {
        baseOps.editConfigRunning(callback, baseOps.createEditConfigStructure(
            Optional.of(ImmutableNodes.leafNode(LEAF_A_NID, "leaf-value")),
            Optional.of(EffectiveOperation.REPLACE),
            YangInstanceIdentifier.builder().node(CONTAINER_C_NID).node(LEAF_A_NID).build()),
            EffectiveOperation.MERGE, true);
        verifyMessageSent("edit-config-test-module-running", NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME);
    }

    @Test
    public void testGetWithFields() throws ExecutionException, InterruptedException {
        final YangInstanceIdentifier path = YangInstanceIdentifier.of(CONTAINER_C_NID);
        final YangInstanceIdentifier leafAField = YangInstanceIdentifier.of(LEAF_A_NID);
        final YangInstanceIdentifier leafBField = YangInstanceIdentifier.of(LEAF_B_NID);

        baseOps.getData(callback, Optional.of(path), List.of(leafAField, leafBField)).get();
        verify(listener).sendRequest(msg("/netconfMessages/get-fields-request.xml"),
                eq(NetconfMessageTransformUtil.NETCONF_GET_QNAME));
    }

    @Test
    public void testGetConfigWithFields() throws ExecutionException, InterruptedException {
        final YangInstanceIdentifier path = YangInstanceIdentifier.of(CONTAINER_C_NID);
        final YangInstanceIdentifier leafAField = YangInstanceIdentifier.of(LEAF_A_NID);
        final YangInstanceIdentifier leafBField = YangInstanceIdentifier.of(LEAF_B_NID);

        baseOps.getConfigRunningData(callback, Optional.of(path), List.of(leafAField, leafBField)).get();
        verify(listener).sendRequest(msg("/netconfMessages/get-config-fields-request.xml"),
                eq(NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME));
    }

    @Test
    public void testGetDataWithoutFields() {
        assertThrows(ExecutionException.class, () -> baseOps.getData(callback,
                Optional.of(YangInstanceIdentifier.of()), List.of()).get());
    }

    @Test
    public void getConfigRunningDataWithoutFields() {
        assertThrows(ExecutionException.class, () -> baseOps.getConfigRunningData(callback,
                Optional.of(YangInstanceIdentifier.of()), List.of()).get());
    }

    @Test
    public void testGetWithFieldsAndEmptyParentPath() throws ExecutionException, InterruptedException {
        final YangInstanceIdentifier leafAField = YangInstanceIdentifier.of(CONTAINER_C_NID, LEAF_A_NID);
        final YangInstanceIdentifier leafXField = YangInstanceIdentifier.of(
                CONTAINER_C_NID, CONTAINER_D_NID, LEAF_X_NID);
        final YangInstanceIdentifier leafZField = YangInstanceIdentifier.of(CONTAINER_E_NID, LEAF_Z_NID);

        baseOps.getData(callback, Optional.of(YangInstanceIdentifier.of()),
                List.of(leafAField, leafXField, leafZField)).get();
        verify(listener).sendRequest(msg("/netconfMessages/get-with-multiple-subtrees.xml"),
                eq(NetconfMessageTransformUtil.NETCONF_GET_QNAME));
    }

    @Test
    public void testGetConfigWithFieldsAndEmptyParentPath() throws ExecutionException, InterruptedException {
        final YangInstanceIdentifier leafAField = YangInstanceIdentifier.of(CONTAINER_C_NID, LEAF_A_NID);
        final YangInstanceIdentifier leafXField = YangInstanceIdentifier.of(
                CONTAINER_C_NID, CONTAINER_D_NID, LEAF_X_NID);
        final YangInstanceIdentifier leafZField = YangInstanceIdentifier.of(CONTAINER_E_NID, LEAF_Z_NID);

        baseOps.getConfigRunningData(callback, Optional.of(YangInstanceIdentifier.of()),
                List.of(leafAField, leafXField, leafZField)).get();
        verify(listener).sendRequest(msg("/netconfMessages/get-config-with-multiple-subtrees.xml"),
                eq(NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME));
    }

    @Test
    public void testGetWithRootFieldsAndEmptyParentPath() throws ExecutionException, InterruptedException {
        final YangInstanceIdentifier contCField = YangInstanceIdentifier.of(CONTAINER_C_NID);
        final YangInstanceIdentifier contDField = YangInstanceIdentifier.of(CONTAINER_E_NID);

        baseOps.getData(callback, Optional.of(YangInstanceIdentifier.of()), List.of(contCField, contDField)).get();
        verify(listener).sendRequest(msg("/netconfMessages/get-with-multiple-root-subtrees.xml"),
                eq(NetconfMessageTransformUtil.NETCONF_GET_QNAME));
    }

    private void verifyMessageSent(final String fileName, final QName name) {
        final String path = "/netconfMessages/" + fileName + ".xml";
        verify(listener).sendRequest(msg(path), eq(name));
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
