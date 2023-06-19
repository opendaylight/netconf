/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_GET_QNAME;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMRpcImplementationNotAvailableException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfStateSchemasTest extends AbstractBaseSchemasTest {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfStateSchemasTest.class);
    private static final NetconfSessionPreferences CAPS = NetconfSessionPreferences.fromStrings(Set.of(
        "urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring?module=ietf-netconf-monitoring&amp;revision=2010-10-04"));

    private final RemoteDeviceId deviceId = new RemoteDeviceId("device", new InetSocketAddress(99));
    private final int numberOfSchemas = 73;
    private final int numberOfLegalSchemas = numberOfSchemas - 3;

    private ContainerNode compositeNodeSchemas;

    @Mock
    private DOMRpcService rpc;

    private EffectiveModelContext schemaContext;

    @Before
    public void setUp() throws Exception {
        schemaContext = BASE_SCHEMAS.getBaseSchemaWithNotifications().getEffectiveModelContext();

        final NormalizationResultHolder resultHolder = new NormalizationResultHolder();
        final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
        final XmlParserStream xmlParser = XmlParserStream.create(writer,
            SchemaInferenceStack.ofDataTreePath(schemaContext, NetconfState.QNAME, Schemas.QNAME).toInference(), false);

        xmlParser.parse(UntrustedXML.createXMLStreamReader(getClass().getResourceAsStream(
                "/netconf-state.schemas.payload.xml")));
        compositeNodeSchemas = (ContainerNode) resultHolder.getResult().data();
    }

    @Test
    public void testCreate() throws Exception {
        final NetconfStateSchemas schemas = NetconfStateSchemas.create(deviceId, compositeNodeSchemas);

        final Set<QName> availableYangSchemasQNames = schemas.getAvailableYangSchemasQNames();
        assertEquals(numberOfLegalSchemas, availableYangSchemasQNames.size());

        assertThat(availableYangSchemasQNames,
                hasItem(QName.create("urn:TBD:params:xml:ns:yang:network-topology", "2013-07-12", "network-topology")));
    }

    @Ignore
    @Test
    public void testCreate2() throws Exception {
        final ContainerNode netconfState = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(NetconfState.QNAME))
                .withChild(compositeNodeSchemas)
                .build();
        final ContainerNode data = Builders.containerBuilder()
                .withNodeIdentifier(NetconfMessageTransformUtil.NETCONF_DATA_NODEID)
                .withChild(netconfState)
                .build();
        final ContainerNode rpcReply = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier
                        .NodeIdentifier(NetconfMessageTransformUtil.NETCONF_RPC_REPLY_QNAME))
                .withChild(data)
                .build();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(rpcReply))).when(rpc)
            .invokeRpc(eq(NETCONF_GET_QNAME), any());
        final NetconfStateSchemas stateSchemas = NetconfStateSchemas.create(rpc, CAPS, deviceId, schemaContext);
        final Set<QName> availableYangSchemasQNames = stateSchemas.getAvailableYangSchemasQNames();
        assertEquals(numberOfLegalSchemas, availableYangSchemasQNames.size());

        assertThat(availableYangSchemasQNames,
                hasItem(QName.create("urn:TBD:params:xml:ns:yang:network-topology", "2013-07-12", "network-topology")));
    }

    @Test
    public void testCreateMonitoringNotSupported() throws Exception {
        final NetconfSessionPreferences caps = NetconfSessionPreferences.fromStrings(Set.of());
        final NetconfStateSchemas stateSchemas = NetconfStateSchemas.create(rpc, caps, deviceId, schemaContext);
        final Set<QName> availableYangSchemasQNames = stateSchemas.getAvailableYangSchemasQNames();
        assertTrue(availableYangSchemasQNames.isEmpty());
    }

    @Test
    public void testCreateFail() throws Exception {
        when(rpc.invokeRpc(eq(NETCONF_GET_QNAME), any())).thenReturn(
                Futures.immediateFailedFuture(new DOMRpcImplementationNotAvailableException("not available")));
        final NetconfStateSchemas stateSchemas = NetconfStateSchemas.create(rpc, CAPS, deviceId, schemaContext);
        final Set<QName> availableYangSchemasQNames = stateSchemas.getAvailableYangSchemasQNames();
        assertTrue(availableYangSchemasQNames.isEmpty());
    }

    @Test
    public void testCreateRpcError() throws Exception {
        final RpcError rpcError = RpcResultBuilder.newError(ErrorType.RPC, new ErrorTag("fail"), "fail");
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(rpcError))).when(rpc)
            .invokeRpc(eq(NETCONF_GET_QNAME), any());
        final NetconfStateSchemas stateSchemas = NetconfStateSchemas.create(rpc, CAPS, deviceId, schemaContext);
        final Set<QName> availableYangSchemasQNames = stateSchemas.getAvailableYangSchemasQNames();
        assertTrue(availableYangSchemasQNames.isEmpty());
    }

    @Test
    public void testCreateInterrupted() {
        //NetconfStateSchemas.create calls Thread.currentThread().interrupt(), so it must run in its own thread
        final Future<?> testFuture = Executors.newSingleThreadExecutor().submit(() -> {
            final ListenableFuture<DOMRpcResult> interruptedFuture = mock(ListenableFuture.class);
            try {
                when(interruptedFuture.get()).thenThrow(new InterruptedException("interrupted"));
                doReturn(interruptedFuture).when(rpc).invokeRpc(eq(NETCONF_GET_QNAME), any());
                NetconfStateSchemas.create(rpc, CAPS, deviceId, schemaContext);
            } catch (final InterruptedException | ExecutionException e) {
                LOG.info("Operation failed.", e);
            }
        });

        assertThat(assertThrows(ExecutionException.class, () -> testFuture.get(3, TimeUnit.SECONDS)).getCause(),
            instanceOf(RuntimeException.class));
    }

    @Test
    public void testRemoteYangSchemaEquals() throws Exception {
        final NetconfStateSchemas.RemoteYangSchema schema1 =
                new NetconfStateSchemas.RemoteYangSchema(NetconfState.QNAME);
        final NetconfStateSchemas.RemoteYangSchema schema2 =
                new NetconfStateSchemas.RemoteYangSchema(NetconfState.QNAME);
        final NetconfStateSchemas.RemoteYangSchema schema3 =
                new NetconfStateSchemas.RemoteYangSchema(Schemas.QNAME);
        assertEquals(schema1, schema2);
        assertEquals(schema2, schema1);
        assertNotEquals(schema1, schema3);
        assertNotEquals(schema2, schema3);
    }
}
