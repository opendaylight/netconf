/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.google.common.util.concurrent.Futures;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcImplementationNotAvailableException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@ExtendWith(MockitoExtension.class)
class RestconfOperationsPostTest extends AbstractRestconfTest {
    private static final QName RPC = QName.create("invoke:rpc:module", "2013-12-03", "rpc-test");
    private static final ContainerNode INPUT = Builders.containerBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create(RPC, "input")))
        .withChild(Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(QName.create(RPC, "cont")))
            .withChild(ImmutableNodes.leafNode(QName.create(RPC, "lf"), "test"))
            .build())
        .build();
    private static final ContainerNode OUTPUT = Builders.containerBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create(RPC, "output")))
        .withChild(ImmutableNodes.leafNode(QName.create(RPC, "content"), "operation result"))
        .build();
    private static final EffectiveModelContext MODEL_CONTEXT =
        YangParserTestUtils.parseYangResourceDirectory("/invoke-rpc");

    @Mock
    private DOMNotificationService notificationService;

    @Override
    EffectiveModelContext modelContext() {
        return MODEL_CONTEXT;
    }

    @Test
    void testInvokeRpcWithNonEmptyOutput() {
        final var result = mock(ContainerNode.class);
        doReturn(false).when(result).isEmpty();

        prepNNC(result);
        assertSame(result, assertNormalizedNode(200, ar -> restconf.operationsXmlPOST(
            apiPath("invoke-rpc-module:rpc-test"), stringInputStream("""
                <input xmlns="invoke:rpc:module"/>"""), uriInfo, ar)));
    }

    @Test
    void testInvokeRpcWithEmptyOutput() {
        final var result = mock(ContainerNode.class);
        doReturn(true).when(result).isEmpty();

        prepNNC(result);
        assertNull(assertEntity(204, ar -> restconf.operationsJsonPOST(apiPath("invoke-rpc-module:rpc-test"),
            stringInputStream("""
                {
                  "invoke-rpc-module:input" : {
                  }
                }"""), uriInfo, ar)));
    }

    @Test
    void invokeRpcTest() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(OUTPUT, List.of()))).when(rpcService)
            .invokeRpc(RPC, INPUT);

        assertEquals(OUTPUT, assertNormalizedNode(200, ar -> restconf.operationsXmlPOST(
            apiPath("invoke-rpc-module:rpc-test"), stringInputStream("""
                <input xmlns="invoke:rpc:module">
                  <cont>
                    <lf>test</lf>
                  </cont>
                </input>"""), uriInfo, ar)));
    }

    @Test
    void invokeRpcErrorsAndCheckTestTest() throws Exception {
        final var exception = new DOMRpcImplementationNotAvailableException(
                "No implementation of RPC " + RPC + " available.");
        doReturn(Futures.immediateFailedFuture(exception)).when(rpcService).invokeRpc(RPC, INPUT);

        final var error = assertError(ar -> restconf.operationsJsonPOST(apiPath("invoke-rpc-module:rpc-test"),
            stringInputStream("""
                {
                  "invoke-rpc-module:input" : {
                    "cont" : {
                      "lf" : "test"
                    }
                  }
                }"""), uriInfo, ar));
        assertEquals("No implementation of RPC (invoke:rpc:module?revision=2013-12-03)rpc-test available.",
            error.getErrorMessage());
        assertEquals(ErrorType.RPC, error.getErrorType());
        assertEquals(ErrorTag.OPERATION_FAILED, error.getErrorTag());
    }

    @Test
    void invokeRpcViaMountPointTest() throws Exception {
        doReturn(Optional.of(new FixedDOMSchemaService(MODEL_CONTEXT))).when(mountPoint)
            .getService(DOMSchemaService.class);
        doReturn(Optional.of(rpcService)).when(mountPoint).getService(DOMRpcService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMActionService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMMountPointService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(NetconfDataTreeService.class);
        doReturn(Optional.of(dataBroker)).when(mountPoint).getService(DOMDataBroker.class);
        doReturn(Optional.of(mountPoint)).when(mountPointService).getMountPoint(YangInstanceIdentifier.of(
            QName.create("urn:ietf:params:xml:ns:yang:ietf-yang-library", "2019-01-04", "modules-state")));
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(OUTPUT, List.of()))).when(rpcService)
            .invokeRpc(RPC, INPUT);

        assertEquals(OUTPUT, assertNormalizedNode(200,
            ar -> restconf.operationsJsonPOST(
                apiPath("ietf-yang-library:modules-state/yang-ext:mount/invoke-rpc-module:rpc-test"),
                stringInputStream("""
                    {
                      "invoke-rpc-module:input" : {
                        "cont" : {
                          "lf" : "test"
                        }
                      }
                    }"""), uriInfo, ar)));
    }

    @Test
    void invokeRpcMissingMountPointServiceTest() {
        doReturn(Optional.of(new FixedDOMSchemaService(MODEL_CONTEXT))).when(mountPoint)
            .getService(DOMSchemaService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMRpcService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMActionService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMMountPointService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(NetconfDataTreeService.class);
        doReturn(Optional.of(dataBroker)).when(mountPoint).getService(DOMDataBroker.class);
        doReturn(Optional.of(mountPoint)).when(mountPointService).getMountPoint(YangInstanceIdentifier.of(
            QName.create("urn:ietf:params:xml:ns:yang:ietf-yang-library", "2019-01-04", "modules-state")));

        final var error = assertError(
            ar -> restconf.operationsJsonPOST(
                apiPath("ietf-yang-library:modules-state/yang-ext:mount/invoke-rpc-module:rpc-test"),
                stringInputStream("""
                    {
                      "invoke-rpc-module:input" : {
                      }
                    }"""), uriInfo, ar));
        assertEquals("RPC invocation is not available", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.OPERATION_NOT_SUPPORTED, error.getErrorTag());
    }

    @Test
    void checkResponseTest() throws Exception {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(OUTPUT, List.of())))
            .when(rpcService).invokeRpc(RPC, INPUT);

        assertEquals(OUTPUT, assertNormalizedNode(200, ar -> restconf.operationsJsonPOST(
            apiPath("invoke-rpc-module:rpc-test"),
            stringInputStream("""
                {
                  "invoke-rpc-module:input" : {
                    "cont" : {
                      "lf" : "test"
                    }
                  }
                }"""), uriInfo, ar)));
    }

    private void prepNNC(final ContainerNode result) {
        final var qname = QName.create("invoke:rpc:module", "2013-12-03", "rpc-test");
        final var domRpcResult = mock(DOMRpcResult.class);
        doReturn(Futures.immediateFuture(domRpcResult)).when(rpcService).invokeRpc(eq(qname), any(ContainerNode.class));
        doReturn(result).when(domRpcResult).value();
    }
}
