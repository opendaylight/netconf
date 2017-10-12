/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.toPath;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcImplementationNotAvailableException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.md.sal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseSchema;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfStateSchemasTest {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfStateSchemasTest.class);

    private static final NetconfSessionPreferences CAPS = NetconfSessionPreferences.fromStrings(Collections.singleton(
        "urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring?module=ietf-netconf-monitoring&amp;revision=2010-10-04"));
    private final RemoteDeviceId deviceId = new RemoteDeviceId("device", new InetSocketAddress(99));
    private final int numberOfSchemas = 73;
    private final int numberOfLegalSchemas = numberOfSchemas - 3;
    private ContainerNode compositeNodeSchemas;

    @Mock
    private DOMRpcService rpc;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        final SchemaContext schemaContext = BaseSchema.BASE_NETCONF_CTX_WITH_NOTIFICATIONS.getSchemaContext();
        final DataSchemaNode schemasNode =
                ((ContainerSchemaNode) schemaContext
                        .getDataChildByName(NetconfState.QNAME)).getDataChildByName(Schemas.QNAME);

        final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
        final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
        final XmlParserStream xmlParser = XmlParserStream.create(writer, schemaContext, schemasNode, false);

        xmlParser.parse(UntrustedXML.createXMLStreamReader(getClass().getResourceAsStream(
                "/netconf-state.schemas.payload.xml")));
        compositeNodeSchemas = (ContainerNode) resultHolder.getResult();

    }

    @Test
    public void testCreate() throws Exception {
        final NetconfStateSchemas schemas = NetconfStateSchemas.create(deviceId, compositeNodeSchemas);

        final Set<QName> availableYangSchemasQNames = schemas.getAvailableYangSchemasQNames();
        assertEquals(numberOfLegalSchemas, availableYangSchemasQNames.size());

        assertThat(availableYangSchemasQNames,
                hasItem(QName.create("urn:TBD:params:xml:ns:yang:network-topology", "2013-07-12", "network-topology")));
    }

    @Test
    public void testCreate2() throws Exception {
        final ContainerNode netconfState = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(NetconfState.QNAME))
                .withChild(compositeNodeSchemas)
                .build();
        final ContainerNode data = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier
                        .NodeIdentifier(NetconfMessageTransformUtil.NETCONF_DATA_QNAME))
                .withChild(netconfState)
                .build();
        final ContainerNode rpcReply = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier
                        .NodeIdentifier(NetconfMessageTransformUtil.NETCONF_RPC_REPLY_QNAME))
                .withChild(data)
                .build();
        when(rpc.invokeRpc(eq(toPath(NETCONF_GET_QNAME)), any()))
                .thenReturn(Futures.immediateCheckedFuture(new DefaultDOMRpcResult(rpcReply)));
        final NetconfStateSchemas stateSchemas = NetconfStateSchemas.create(rpc, CAPS, deviceId);
        final Set<QName> availableYangSchemasQNames = stateSchemas.getAvailableYangSchemasQNames();
        assertEquals(numberOfLegalSchemas, availableYangSchemasQNames.size());

        assertThat(availableYangSchemasQNames,
                hasItem(QName.create("urn:TBD:params:xml:ns:yang:network-topology", "2013-07-12", "network-topology")));
    }

    @Test
    public void testCreateMonitoringNotSupported() throws Exception {
        final NetconfSessionPreferences caps = NetconfSessionPreferences.fromStrings(Collections.emptySet());
        final NetconfStateSchemas stateSchemas = NetconfStateSchemas.create(rpc, caps, deviceId);
        final Set<QName> availableYangSchemasQNames = stateSchemas.getAvailableYangSchemasQNames();
        Assert.assertTrue(availableYangSchemasQNames.isEmpty());
    }

    @Test
    public void testCreateFail() throws Exception {
        final CheckedFuture<DOMRpcResult, DOMRpcException> resultFuture =
                Futures.immediateFailedCheckedFuture(new DOMRpcImplementationNotAvailableException("not available"));
        when(rpc.invokeRpc(eq(toPath(NETCONF_GET_QNAME)), any())).thenReturn(resultFuture);
        final NetconfStateSchemas stateSchemas = NetconfStateSchemas.create(rpc, CAPS, deviceId);
        final Set<QName> availableYangSchemasQNames = stateSchemas.getAvailableYangSchemasQNames();
        Assert.assertTrue(availableYangSchemasQNames.isEmpty());
    }

    @Test
    public void testCreateRpcError() throws Exception {
        final RpcError rpcError = RpcResultBuilder.newError(RpcError.ErrorType.RPC, "fail", "fail");
        when(rpc.invokeRpc(eq(toPath(NETCONF_GET_QNAME)), any()))
                .thenReturn(Futures.immediateCheckedFuture(new DefaultDOMRpcResult(rpcError)));
        final NetconfStateSchemas stateSchemas = NetconfStateSchemas.create(rpc, CAPS, deviceId);
        final Set<QName> availableYangSchemasQNames = stateSchemas.getAvailableYangSchemasQNames();
        Assert.assertTrue(availableYangSchemasQNames.isEmpty());
    }

    @SuppressWarnings({ "checkstyle:IllegalThrows", "checkstyle:avoidHidingCauseException" })
    @Test(expected = RuntimeException.class)
    public void testCreateInterrupted() throws Throwable {
        //NetconfStateSchemas.create calls Thread.currentThread().interrupt(), so it must run in its own thread
        final Future<?> testFuture = Executors.newSingleThreadExecutor().submit(() -> {
            final ListenableFuture interruptedFuture = mock(ListenableFuture.class);
            try {
                when(interruptedFuture.get()).thenThrow(new InterruptedException("interrupted"));
                final CheckedFuture checkedFuture = Futures.makeChecked(interruptedFuture, ReadFailedException.MAPPER);
                when(rpc.invokeRpc(eq(toPath(NETCONF_GET_QNAME)), any())).thenReturn(checkedFuture);
                NetconfStateSchemas.create(rpc, CAPS, deviceId);
            } catch (final InterruptedException | ExecutionException e) {
                LOG.info("Operation failed.", e);
            }

        });
        try {
            testFuture.get(3, TimeUnit.SECONDS);
        } catch (final ExecutionException e) {
            throw e.getCause();
        }
    }

    @Test
    public void testRemoteYangSchemaEquals() throws Exception {
        final NetconfStateSchemas.RemoteYangSchema schema1 =
                new NetconfStateSchemas.RemoteYangSchema(NetconfState.QNAME);
        final NetconfStateSchemas.RemoteYangSchema schema2 =
                new NetconfStateSchemas.RemoteYangSchema(NetconfState.QNAME);
        final NetconfStateSchemas.RemoteYangSchema schema3 =
                new NetconfStateSchemas.RemoteYangSchema(Schemas.QNAME);
        Assert.assertEquals(schema1, schema2);
        Assert.assertEquals(schema2, schema1);
        Assert.assertNotEquals(schema1, schema3);
        Assert.assertNotEquals(schema2, schema3);

    }
}
