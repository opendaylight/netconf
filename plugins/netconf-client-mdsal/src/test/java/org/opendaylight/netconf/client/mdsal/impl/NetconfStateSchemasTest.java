/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMRpcImplementationNotAvailableException;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.client.mdsal.AbstractBaseSchemasTest;
import org.opendaylight.netconf.client.mdsal.api.NetconfDeviceSchemas;
import org.opendaylight.netconf.client.mdsal.api.NetconfRpcService;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.ProvidedSources;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Get;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;

@ExtendWith(MockitoExtension.class)
class NetconfStateSchemasTest extends AbstractBaseSchemasTest {
    private static final NetconfSessionPreferences CAPS = NetconfSessionPreferences.fromStrings(Set.of(
        "urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring?module=ietf-netconf-monitoring&amp;revision=2010-10-04"));
    private static final RemoteDeviceId DEVICE_ID = new RemoteDeviceId("device", new InetSocketAddress(99));

    private static EffectiveModelContext MODEL_CONTEXT = BASE_SCHEMAS.baseSchemaForCapabilities(CAPS).modelContext();
    private static ContainerNode SCHEMAS_PAYLOAD;

    @Mock
    private NetconfRpcService rpc;

    @BeforeAll
    static void setUp() throws Exception {
        final var resultHolder = new NormalizationResultHolder();
        final var writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
        final var xmlParser = XmlParserStream.create(writer,
            SchemaInferenceStack.ofDataTreePath(MODEL_CONTEXT, NetconfState.QNAME, Schemas.QNAME).toInference(), false);

        xmlParser.parse(UntrustedXML.createXMLStreamReader(NetconfStateSchemasTest.class.getResourceAsStream(
                "/netconf-state.schemas.payload.xml")));
        SCHEMAS_PAYLOAD = (ContainerNode) resultHolder.getResult().data();
    }

    @Test
    void testTesolveMonitoringSources() {
        final var providedSchemas = NetconfStateSchemasResolverImpl.resolveMonitoringSources(DEVICE_ID, rpc,
            SCHEMAS_PAYLOAD);

        final var availableYangSchemasQNames = availableYangSchemasQNames(providedSchemas);

        assertEquals(69, availableYangSchemasQNames.size());

        assertThat(availableYangSchemasQNames,
                hasItem(QName.create("urn:TBD:params:xml:ns:yang:network-topology", "2013-07-12", "network-topology")));
    }

    @Test
    @Disabled("We cannot handle a container as data -- only anyxml")
    void testCreate2() {
        final ContainerNode rpcReply = ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(NetconfMessageTransformUtil.NETCONF_OUTPUT_NODEID)
                .withChild(ImmutableNodes.newContainerBuilder()
                    .withNodeIdentifier(NetconfMessageTransformUtil.NETCONF_DATA_NODEID)
                    .withChild(ImmutableNodes.newContainerBuilder()
                        .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(NetconfState.QNAME))
                        .withChild(SCHEMAS_PAYLOAD)
                        .build())
                    .build())
                .build();
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(rpcReply))).when(rpc)
            .invokeNetconf(eq(Get.QNAME), any());
        final var stateSchemas = assertSchemas(CAPS);
        final var availableYangSchemasQNames = availableYangSchemasQNames(stateSchemas.providedSources());
        assertEquals(69, availableYangSchemasQNames.size());

        assertThat(availableYangSchemasQNames,
                hasItem(QName.create("urn:TBD:params:xml:ns:yang:network-topology", "2013-07-12", "network-topology")));
    }

    @Test
    void testCreateMonitoringNotSupported() {
        final var stateSchemas = assertSchemas(NetconfSessionPreferences.fromStrings(Set.of()));
        assertEquals(Set.of(), availableYangSchemasQNames(stateSchemas.providedSources()));
    }

    @Test
    void testCreateFail() {
        final var domEx = new DOMRpcImplementationNotAvailableException("not available");
        doReturn(Futures.immediateFailedFuture(domEx)).when(rpc).invokeNetconf(eq(Get.QNAME), any());
        assertSame(domEx, assertSchemasFailure());
    }

    @Test
    void testCreateRpcError() {
        final var rpcError = RpcResultBuilder.newError(ErrorType.RPC, new ErrorTag("fail"), "fail");
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(rpcError))).when(rpc)
            .invokeNetconf(eq(Get.QNAME), any());

        final var stateSchemas = assertSchemas(CAPS);
        assertEquals(List.of(), stateSchemas.providedSources());
    }

    private NetconfDeviceSchemas assertSchemas(final NetconfSessionPreferences prefs) {
        try {
            return Futures.getDone(new NetconfStateSchemasResolverImpl().resolve(DEVICE_ID, prefs, rpc, MODEL_CONTEXT));
        } catch (ExecutionException e) {
            throw new AssertionError(e);
        }
    }

    private Throwable assertSchemasFailure() {
        final var future = new NetconfStateSchemasResolverImpl().resolve(DEVICE_ID, CAPS, rpc, MODEL_CONTEXT);
        return assertThrows(ExecutionException.class, () -> Futures.getDone(future)).getCause();
    }

    private static Set<QName> availableYangSchemasQNames(final List<ProvidedSources<?>> providedSources) {
        return providedSources.stream()
            .flatMap(sources -> sources.sources().stream())
            .collect(Collectors.toSet());
    }
}
