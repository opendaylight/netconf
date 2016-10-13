/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.pipeline;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import akka.actor.TypedProps;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataChangeListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcImplementationNotAvailableException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.md.sal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.util.messages.NormalizedNodeMessage;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnyXmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.CrossSourceStatementReactor;
import org.opendaylight.yangtools.yang.parser.stmt.rfc6020.YangInferencePipeline;
import org.w3c.dom.Element;
import scala.concurrent.Future;

public class NetconfDeviceMasterDataBrokerTest {

    private static final String EDIT_CFG_XML_1 =
            "<config xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n" +
                    "<c xmlns=\"test:namespace\">\n" +
                    "<a xmlns:a=\"urn:ietf:params:xml:ns:netconf:base:1.0\" a:operation=\"replace\">a_value</a>\n" +
                    "</c>\n" +
                    "</config>\n";
    private static final String EDIT_CFG_XML_2 =
            "<config xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n" +
                    "<c xmlns=\"test:namespace\">\n" +
                    "<a>a_value</a>\n" +
                    "</c>\n" +
                    "</config>\n";
    private static final String EDIT_CFG_XML_3 =
            "<config xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n" +
                    "<c xmlns=\"test:namespace\">\n" +
                    "<b xmlns:a=\"urn:ietf:params:xml:ns:netconf:base:1.0\" a:operation=\"delete\"/>\n" +
                    "</c>\n" +
                    "</config>\n";
    private static final QName C_NAME = QName.create("test:namespace", "2013-07-22", "c");
    private static final QName A_NAME = QName.create(C_NAME, "a");
    private static final QName B_NAME = QName.create(C_NAME, "b");
    private static final SchemaPath GET_CFG_PATH = SchemaPath.create(true, NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME);
    private static final SchemaPath LOCK_PATH = SchemaPath.create(true, NetconfMessageTransformUtil.NETCONF_LOCK_QNAME);
    private static final SchemaPath EDIT_CFG_PATH = SchemaPath.create(true, NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME);
    private static final SchemaPath UNLOCK_PATH = SchemaPath.create(true, NetconfMessageTransformUtil.NETCONF_UNLOCK_QNAME);
    private static final YangInstanceIdentifier cPath = YangInstanceIdentifier.create().node(C_NAME);
    private static final YangInstanceIdentifier aPath = YangInstanceIdentifier.builder(cPath).node(A_NAME).build();
    private static final YangInstanceIdentifier bPath = YangInstanceIdentifier.builder(cPath).node(B_NAME).build();
    private static final LeafNode<Object> a = Builders.leafBuilder()
            .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(A_NAME))
            .withValue("a_value")
            .build();
    private static final ContainerNode c = Builders.containerBuilder()
            .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(C_NAME))
            .withChild(a)
            .build();

    @Mock
    private DOMRpcService rpc;
    private ActorSystem system;
    private NetconfDeviceMasterDataBroker broker;
    private ContainerNode data;
    private RemoteDeviceId deviceId;
    private SchemaContext schemaContext;
    private NetconfSessionPreferences preferences;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        system = ActorSystem.apply();
        deviceId = new RemoteDeviceId("device-1", InetSocketAddress.createUnresolved("localhost", 17830));
        schemaContext = parseYangStreams(ImmutableList.of(getClass().getResourceAsStream("/yang/test-module.yang")));
        final ImmutableSet<String> caps = ImmutableSet.of(NetconfMessageTransformUtil.NETCONF_RUNNING_WRITABLE_URI.toString());
        preferences = NetconfSessionPreferences.fromStrings(caps);

        broker = new NetconfDeviceMasterDataBroker(system, deviceId, schemaContext, rpc, preferences);

        data = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(NetconfMessageTransformUtil.NETCONF_DATA_QNAME))
                .withChild(c)
                .build();
        when(rpc.invokeRpc(eq(LOCK_PATH), any())).thenReturn(Futures.immediateCheckedFuture(new DefaultDOMRpcResult()));
        when(rpc.invokeRpc(eq(UNLOCK_PATH), any())).thenReturn(Futures.immediateCheckedFuture(new DefaultDOMRpcResult()));
        when(rpc.invokeRpc(eq(EDIT_CFG_PATH), any())).thenReturn(Futures.immediateCheckedFuture(new DefaultDOMRpcResult()));
        final ContainerNode getConfig = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME))
                .withChild(data)
                .build();
        final DOMRpcResult result = new DefaultDOMRpcResult(getConfig);
        when(rpc.invokeRpc(eq(GET_CFG_PATH), any())).thenReturn(Futures.immediateCheckedFuture(result));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void registerDataChangeListener() throws Exception {
        final DOMDataChangeListener listener = mock(DOMDataChangeListener.class);
        broker.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.EMPTY,
                listener, AsyncDataBroker.DataChangeScope.BASE);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void createTransactionChain() throws Exception {
        final TransactionChainListener listener = mock(TransactionChainListener.class);
        broker.createTransactionChain(listener);
    }

    @Test
    public void getSupportedExtensions() throws Exception {
        Assert.assertTrue(broker.getSupportedExtensions().isEmpty());
    }

    @Test
    public void read() throws Exception {
        final Future<Optional<NormalizedNodeMessage>> resultFuture =
                broker.read(LogicalDatastoreType.CONFIGURATION, aPath);
        Assert.assertTrue(resultFuture.isCompleted());
        final Optional<NormalizedNodeMessage> normalizedNodeMessageOptional = resultFuture.value().get().get();
        Assert.assertTrue(normalizedNodeMessageOptional.isPresent());
        final NormalizedNode<?, ?> node = normalizedNodeMessageOptional.get().getNode();
        Assert.assertEquals(a, node);
    }

    @Test
    public void exists() throws Exception {
        final Future<Boolean> resultFuture =
                broker.exists(LogicalDatastoreType.CONFIGURATION, aPath);
        Assert.assertTrue(resultFuture.isCompleted());
        final Boolean normalizedNodeMessageOptional = resultFuture.value().get().get();
        Assert.assertTrue(normalizedNodeMessageOptional);
    }

    @Test
    public void put() throws Exception {
        final NormalizedNodeMessage msg = new NormalizedNodeMessage(aPath, a);
        broker.put(LogicalDatastoreType.CONFIGURATION, msg);
        broker.submit();
        checkRpc(EDIT_CFG_XML_1);
    }

    @Test
    public void merge() throws Exception {
        final NormalizedNodeMessage msg = new NormalizedNodeMessage(cPath, c);
        broker.merge(LogicalDatastoreType.CONFIGURATION, msg);
        broker.submit();
        checkRpc(EDIT_CFG_XML_2);
    }

    @Test
    public void delete() throws Exception {
        broker.delete(LogicalDatastoreType.CONFIGURATION, bPath);
        broker.submit();
        checkRpc(EDIT_CFG_XML_3);
    }

    @Test
    public void commit() throws Exception {
        broker.delete(LogicalDatastoreType.CONFIGURATION, bPath);
        broker.commit();
        checkRpc(EDIT_CFG_XML_3);
    }

    @Test
    public void newReadOnlyTx() throws Exception {
        final ProxyNetconfDeviceDataBroker brokerActor = getNetconfDeviceDataBrokerActor();
        final DOMDataReadOnlyTransaction readTx = brokerActor.newReadOnlyTransaction();
        readTx.read(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.EMPTY);
        verify(rpc, timeout(1000)).invokeRpc(eq(SchemaPath.create(true, NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME)), any());
    }

    @Test
    public void newWriteOnlyTx() throws Exception {
        final ProxyNetconfDeviceDataBroker brokerActor = getNetconfDeviceDataBrokerActor();
        final DOMDataWriteTransaction writeTx = brokerActor.newWriteOnlyTransaction();
        writeTx.put(LogicalDatastoreType.CONFIGURATION, aPath, a);
        writeTx.submit();
        checkActorRpc();
    }

    @Test
    public void newReadWriteTx() throws Exception {
        final ProxyNetconfDeviceDataBroker brokerActor = getNetconfDeviceDataBrokerActor();
        final DOMDataReadWriteTransaction writeTx = brokerActor.newReadWriteTransaction();
        writeTx.put(LogicalDatastoreType.CONFIGURATION, aPath, a);
        writeTx.submit();
        checkActorRpc();
    }

    @Test(expected = NetconfDocumentedException.class)
    public void submitFailure() throws Exception {
        final NetconfDeviceMasterDataBroker failBroker = getFailBroker();
        final NormalizedNodeMessage msg = new NormalizedNodeMessage(aPath, a);
        failBroker.put(LogicalDatastoreType.CONFIGURATION, msg);
        failBroker.submit().value().get().get();
    }

    @Test(expected = NetconfDocumentedException.class)
    public void commitFailure() throws Exception {
        final NetconfDeviceMasterDataBroker failBroker = getFailBroker();
        final NormalizedNodeMessage msg = new NormalizedNodeMessage(aPath, a);
        failBroker.put(LogicalDatastoreType.CONFIGURATION, msg);
        failBroker.commit().value().get().get();
    }

    @Test(expected = ReadFailedException.class)
    public void readFailure() throws Exception {
        final NetconfDeviceMasterDataBroker failBroker = getFailBroker();
        failBroker.read(LogicalDatastoreType.CONFIGURATION, aPath).value().get().get();
    }

    @Test(expected = ReadFailedException.class)
    public void existFailure() throws Exception {
        final NetconfDeviceMasterDataBroker failBroker = getFailBroker();
        failBroker.exists(LogicalDatastoreType.CONFIGURATION, aPath).value().get().get();
    }

    private NetconfDeviceMasterDataBroker getFailBroker() {
        final DOMRpcService failRpc = mock(DOMRpcService.class);
        when(failRpc.invokeRpc(eq(LOCK_PATH), any())).thenReturn(Futures.immediateCheckedFuture(new DefaultDOMRpcResult()));
        when(failRpc.invokeRpc(eq(UNLOCK_PATH), any())).thenReturn(Futures.immediateCheckedFuture(new DefaultDOMRpcResult()));
        final CheckedFuture<DOMRpcResult, DOMRpcException> failedFuture =
                Futures.immediateFailedCheckedFuture(new DOMRpcImplementationNotAvailableException("RPC failed"));
        when(failRpc.invokeRpc(eq(EDIT_CFG_PATH), any())).thenReturn(failedFuture);
        when(failRpc.invokeRpc(eq(GET_CFG_PATH), any())).thenReturn(failedFuture);
        return new NetconfDeviceMasterDataBroker(system, deviceId, schemaContext, failRpc, preferences);
    }

    private static SchemaContext parseYangStreams(final List<InputStream> streams) {
        final CrossSourceStatementReactor.BuildAction reactor = YangInferencePipeline.RFC6020_REACTOR
                .newBuild();
        final SchemaContext schemaContext;
        try {
            schemaContext = reactor.buildEffective(streams);
        } catch (final ReactorException e) {
            throw new RuntimeException("Unable to build schema context from " + streams, e);
        }
        return schemaContext;
    }

    private void checkEditConfigContent(final NormalizedNode editConfig, final String expContent) {
        final ContainerNode editConfigCont = (ContainerNode) editConfig;
        final QName configQname = NetconfMessageTransformUtil.NETCONF_CONFIG_QNAME;
        final ChoiceNode content = (ChoiceNode) editConfigCont.getChild(new YangInstanceIdentifier.NodeIdentifier(QName.create(configQname, "edit-content"))).get();
        final AnyXmlNode xml = (AnyXmlNode) content.getChild(new YangInstanceIdentifier.NodeIdentifier(NetconfMessageTransformUtil.NETCONF_CONFIG_QNAME)).get();
        Assert.assertEquals(expContent, XmlUtil.toString(XmlElement.fromDomElement((Element) xml.getValue().getNode())));
    }

    private void checkRpc(final String xml) {
        final ArgumentCaptor<NormalizedNode> captor = ArgumentCaptor.forClass(NormalizedNode.class);
        final InOrder inOrder = Mockito.inOrder(rpc);
        inOrder.verify(rpc).invokeRpc(eq(LOCK_PATH), any());
        inOrder.verify(rpc).invokeRpc(eq(EDIT_CFG_PATH), captor.capture());
        inOrder.verify(rpc).invokeRpc(eq(UNLOCK_PATH), any());
        checkEditConfigContent(captor.getValue(), xml);
    }

    private void checkActorRpc() {
        final ArgumentCaptor<NormalizedNode> captor = ArgumentCaptor.forClass(NormalizedNode.class);
        verify(rpc, timeout(1000)).invokeRpc(eq(LOCK_PATH), any());
        verify(rpc, timeout(1000)).invokeRpc(eq(EDIT_CFG_PATH), captor.capture());
        verify(rpc, timeout(1000)).invokeRpc(eq(UNLOCK_PATH), any());
        checkEditConfigContent(captor.getValue(), EDIT_CFG_XML_1);
    }

    private ProxyNetconfDeviceDataBroker getNetconfDeviceDataBrokerActor() {
        final TypedProps<ProxyNetconfDeviceDataBroker> props = new TypedProps<>(ProxyNetconfDeviceDataBroker.class, () -> broker);
        return TypedActor.get(system).typedActorOf(props);
    }
}