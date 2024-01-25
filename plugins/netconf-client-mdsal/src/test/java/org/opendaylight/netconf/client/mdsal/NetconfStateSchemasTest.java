/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_GET_QNAME;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMRpcImplementationNotAvailableException;
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
import org.opendaylight.yangtools.yang.common.OperationFailedException;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfStateSchemasTest extends AbstractBaseSchemasTest {
    private static final NetconfSessionPreferences CAPS = NetconfSessionPreferences.fromStrings(Set.of(
        "urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring?module=ietf-netconf-monitoring&amp;revision=2010-10-04"));

    private static EffectiveModelContext MODEL_CONTEXT;
    private static ContainerNode SCHEMAS_PAYLOAD;

    private final RemoteDeviceId deviceId = new RemoteDeviceId("device", new InetSocketAddress(99));
    private final int numberOfSchemas = 73;
    private final int numberOfLegalSchemas = numberOfSchemas - 3;

    @Mock
    private DOMRpcService rpc;

    @BeforeClass
    public static void setUp() throws Exception {
        MODEL_CONTEXT = BASE_SCHEMAS.baseSchemaWithNotifications().getEffectiveModelContext();

        final var resultHolder = new NormalizationResultHolder();
        final var writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
        final var xmlParser = XmlParserStream.create(writer,
            SchemaInferenceStack.ofDataTreePath(MODEL_CONTEXT, NetconfState.QNAME, Schemas.QNAME).toInference(), false);

        xmlParser.parse(UntrustedXML.createXMLStreamReader(NetconfStateSchemasTest.class.getResourceAsStream(
                "/netconf-state.schemas.payload.xml")));
        SCHEMAS_PAYLOAD = (ContainerNode) resultHolder.getResult().data();
    }

    @Test
    public void testCreate() throws Exception {
        final var future = SettableFuture.<NetconfStateSchemas>create();

        NetconfStateSchemas.create(future, deviceId, SCHEMAS_PAYLOAD);
        final var schemas = Futures.getDone(future);

        final var availableYangSchemasQNames = schemas.getAvailableYangSchemasQNames();
        assertEquals(numberOfLegalSchemas, availableYangSchemasQNames.size());

        assertThat(availableYangSchemasQNames,
                hasItem(QName.create("urn:TBD:params:xml:ns:yang:network-topology", "2013-07-12", "network-topology")));
    }

    @Ignore
    @Test
    public void testCreate2() throws Exception {
        final ContainerNode rpcReply = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(NetconfMessageTransformUtil.NETCONF_RPC_REPLY_QNAME))
                .withChild(Builders.containerBuilder()
                    .withNodeIdentifier(NetconfMessageTransformUtil.NETCONF_DATA_NODEID)
                    .withChild(Builders.containerBuilder()
                        .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(NetconfState.QNAME))
                        .withChild(SCHEMAS_PAYLOAD)
                        .build())
                    .build())
                .build();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(rpcReply))).when(rpc)
            .invokeRpc(eq(NETCONF_GET_QNAME), any());
        final NetconfStateSchemas stateSchemas = assertSchemas(CAPS);
        final Set<QName> availableYangSchemasQNames = stateSchemas.getAvailableYangSchemasQNames();
        assertEquals(numberOfLegalSchemas, availableYangSchemasQNames.size());

        assertThat(availableYangSchemasQNames,
                hasItem(QName.create("urn:TBD:params:xml:ns:yang:network-topology", "2013-07-12", "network-topology")));
    }

    @Test
    public void testCreateMonitoringNotSupported() throws Exception {
        final var stateSchemas = assertSchemas(NetconfSessionPreferences.fromStrings(Set.of()));
        assertEquals(Set.of(), stateSchemas.getAvailableYangSchemasQNames());
    }

    @Test
    public void testCreateFail() throws Exception {
        final var domEx = new DOMRpcImplementationNotAvailableException("not available");
        doReturn(Futures.immediateFailedFuture(domEx)).when(rpc).invokeRpc(eq(NETCONF_GET_QNAME), any());
        assertSame(domEx, assertSchemasFailure());
    }

    @Test
    public void testCreateRpcError() throws Exception {
        final var rpcError = RpcResultBuilder.newError(ErrorType.RPC, new ErrorTag("fail"), "fail");
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(rpcError))).when(rpc)
            .invokeRpc(eq(NETCONF_GET_QNAME), any());

        final var ex = assertInstanceOf(OperationFailedException.class, assertSchemasFailure());
        assertEquals(List.of(rpcError), ex.getErrorList());
    }

    private NetconfStateSchemas assertSchemas(final NetconfSessionPreferences prefs) {
        try {
            return Futures.getDone(NetconfStateSchemas.forDevice(rpc, prefs, deviceId, MODEL_CONTEXT));
        } catch (ExecutionException e) {
            throw new AssertionError(e);
        }
    }

    private Throwable assertSchemasFailure() {
        final var future = NetconfStateSchemas.forDevice(rpc, CAPS, deviceId, MODEL_CONTEXT);
        return assertThrows(ExecutionException.class, () -> Futures.getDone(future)).getCause();
    }
}
