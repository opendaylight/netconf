/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.util;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.sal.connect.api.MessageTransformer;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceRpc;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.NetconfMessageTransformer;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.ModifyAction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.xml.sax.SAXException;

public class NetconfBaseOpsTest {

    static {
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setIgnoreComments(true);
    }

    private static final QName CONTAINER_Q_NAME = QName.create("test:namespace", "2013-07-22", "c");

    @Mock
    private RemoteDeviceCommunicator<NetconfMessage> listener;
    private NetconfRpcFutureCallback callback;
    private NetconfBaseOps baseOps;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
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
        final SchemaContext schemaContext =
                YangParserTestUtils.parseYangResource("/schemas/test-module.yang");
        final MessageTransformer<NetconfMessage> transformer = new NetconfMessageTransformer(schemaContext, true);
        final DOMRpcService rpc = new NetconfDeviceRpc(schemaContext, listener, transformer);
        final RemoteDeviceId id =
                new RemoteDeviceId("device-1", InetSocketAddress.createUnresolved("localhost", 17830));
        callback = new NetconfRpcFutureCallback("prefix", id);
        baseOps = new NetconfBaseOps(rpc, schemaContext);
    }

    @Test
    public void testLock() throws Exception {
        baseOps.lock(callback, NetconfMessageTransformUtil.NETCONF_CANDIDATE_QNAME);
        verifyMessageSent("lock", NetconfMessageTransformUtil.NETCONF_LOCK_QNAME);
    }

    @Test
    public void testLockCandidate() throws Exception {
        baseOps.lockCandidate(callback);
        verifyMessageSent("lock", NetconfMessageTransformUtil.NETCONF_LOCK_QNAME);
    }

    @Test
    public void testUnlock() throws Exception {
        baseOps.unlock(callback, NetconfMessageTransformUtil.NETCONF_CANDIDATE_QNAME);
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
        baseOps.copyConfig(callback, NetconfMessageTransformUtil.NETCONF_RUNNING_QNAME,
                NetconfMessageTransformUtil.NETCONF_CANDIDATE_QNAME);
        verifyMessageSent("copy-config", NetconfMessageTransformUtil.NETCONF_COPY_CONFIG_QNAME);
    }

    @Test
    public void testCopyRunningToCandidate() throws Exception {
        baseOps.copyRunningToCandidate(callback);
        verifyMessageSent("copy-config", NetconfMessageTransformUtil.NETCONF_COPY_CONFIG_QNAME);
    }


    @Test
    public void testGetConfigRunningData() throws Exception {
        final Optional<NormalizedNode<?, ?>> dataOpt =
                baseOps.getConfigRunningData(callback, Optional.of(YangInstanceIdentifier.EMPTY)).get();
        Assert.assertTrue(dataOpt.isPresent());
        Assert.assertEquals(NetconfMessageTransformUtil.NETCONF_DATA_QNAME, dataOpt.get().getNodeType());
    }

    @Test
    public void testGetData() throws Exception {
        final Optional<NormalizedNode<?, ?>> dataOpt =
                baseOps.getData(callback, Optional.of(YangInstanceIdentifier.EMPTY)).get();
        Assert.assertTrue(dataOpt.isPresent());
        Assert.assertEquals(NetconfMessageTransformUtil.NETCONF_DATA_QNAME, dataOpt.get().getNodeType());
    }

    @Test
    public void testGetConfigRunning() throws Exception {
        baseOps.getConfigRunning(callback, Optional.absent());
        verifyMessageSent("getConfig", NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME);
    }

    @Test
    public void testGetConfigCandidate() throws Exception {
        baseOps.getConfigCandidate(callback, Optional.absent());
        verifyMessageSent("getConfig_candidate", NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME);
    }

    @Test
    public void testGetConfigCandidateWithFilter() throws Exception {
        final YangInstanceIdentifier id = YangInstanceIdentifier.builder()
                .node(CONTAINER_Q_NAME)
                .build();
        baseOps.getConfigCandidate(callback, Optional.of(id));
        verifyMessageSent("getConfig_candidate-filter", NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME);
    }

    @Test
    public void testGet() throws Exception {
        baseOps.get(callback, Optional.absent());
        verifyMessageSent("get", NetconfMessageTransformUtil.NETCONF_GET_QNAME);
    }

    @Test
    public void testEditConfigCandidate() throws Exception {
        final QName leafQName = QName.create(CONTAINER_Q_NAME, "a");
        final LeafNode<Object> leaf = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(leafQName))
                .withValue("leaf-value")
                .build();
        final YangInstanceIdentifier leafId = YangInstanceIdentifier.builder()
                .node(CONTAINER_Q_NAME)
                .node(leafQName)
                .build();
        final DataContainerChild<?, ?> structure = baseOps.createEditConfigStrcture(Optional.of(leaf),
                Optional.of(ModifyAction.REPLACE), leafId);
        baseOps.editConfigCandidate(callback, structure, true);
        verifyMessageSent("edit-config-test-module", NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME);
    }

    @Test
    public void testEditConfigRunning() throws Exception {
        final QName containerQName = QName.create("test:namespace", "2013-07-22", "c");
        final QName leafQName = QName.create(containerQName, "a");
        final LeafNode<Object> leaf = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(leafQName))
                .withValue("leaf-value")
                .build();
        final YangInstanceIdentifier leafId = YangInstanceIdentifier.builder()
                .node(containerQName)
                .node(leafQName)
                .build();
        final DataContainerChild<?, ?> structure = baseOps.createEditConfigStrcture(Optional.of(leaf),
                Optional.of(ModifyAction.REPLACE), leafId);
        baseOps.editConfigRunning(callback, structure, ModifyAction.MERGE, true);
        verifyMessageSent("edit-config-test-module-running", NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME);
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

    private static class NetconfMessageMatcher extends BaseMatcher<NetconfMessage> {

        private final Document expected;

        NetconfMessageMatcher(final Document expected) {
            this.expected = removeAttrs(expected);
        }

        @Override
        public boolean matches(final Object item) {
            if (!(item instanceof NetconfMessage)) {
                return false;
            }
            final NetconfMessage message = (NetconfMessage) item;
            final Document actualDoc = removeAttrs(message.getDocument());
            actualDoc.normalizeDocument();
            expected.normalizeDocument();
            final Diff diff = XMLUnit.compareXML(expected, actualDoc);
            return diff.similar();
        }

        @Override
        public void describeTo(final Description description) {
            description.appendText(XmlUtil.toString(expected));
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
