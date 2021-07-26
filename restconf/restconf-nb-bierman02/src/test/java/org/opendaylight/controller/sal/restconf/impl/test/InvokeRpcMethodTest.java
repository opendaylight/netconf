/*
 * Copyright (c) 2013, 2015 Brocade Communication Systems, Inc., Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.restconf.impl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFailedFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.mdsal.dom.api.DOMRpcImplementationNotAvailableException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.sal.restconf.impl.BrokerFacade;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfImpl;
import org.opendaylight.restconf.common.ErrorTags;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.builder.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.api.schema.builder.NormalizedNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.SchemaAwareBuilders;
import org.opendaylight.yangtools.yang.model.api.ContainerLike;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;

public class InvokeRpcMethodTest {

    private static UriInfo uriInfo;
    private static EffectiveModelContext schemaContext;

    private final RestconfImpl restconfImpl;
    private final ControllerContext controllerContext;
    private final BrokerFacade brokerFacade = mock(BrokerFacade.class);

    public InvokeRpcMethodTest() {
        controllerContext = TestRestconfUtils.newControllerContext(schemaContext);
        restconfImpl = RestconfImpl.newInstance(brokerFacade, controllerContext);
    }

    @BeforeClass
    public static void init() throws FileNotFoundException, ReactorException {
        schemaContext = TestUtils.loadSchemaContext("/full-versions/yangs", "/invoke-rpc");
        final Collection<? extends Module> allModules = schemaContext.getModules();
        assertNotNull(allModules);
        final Module module = TestUtils.resolveModule("invoke-rpc-module", allModules);
        assertNotNull(module);

        uriInfo = mock(UriInfo.class);
        final MultivaluedMap<String, String> map = new MultivaluedHashMap<>();
        map.put("prettyPrint", List.of("true"));
        doReturn(map).when(uriInfo).getQueryParameters(any(Boolean.class));
    }

    /**
     * Test method invokeRpc in RestconfImpl class tests if composite node as input parameter of method invokeRpc
     * (second argument) is wrapped to parent composite node which has QName equals to QName of rpc (resolved from
     * string - first argument).
     */
    @Test
    @Ignore
    public void invokeRpcMethodTest() {
        controllerContext.findModuleNameByNamespace(XMLNamespace.of("invoke:rpc:module"));

        final NormalizedNodeContext payload = prepareDomPayload();

        final NormalizedNodeContext rpcResponse =
                restconfImpl.invokeRpc("invoke-rpc-module:rpc-test", payload, uriInfo);
        assertNotNull(rpcResponse);
        assertNull(rpcResponse.getData());

    }

    private NormalizedNodeContext prepareDomPayload() {
        final EffectiveModelContext schema = controllerContext.getGlobalSchema();
        final Module rpcModule = schema.findModules("invoke-rpc-module").iterator().next();
        assertNotNull(rpcModule);
        final QName rpcQName = QName.create(rpcModule.getQNameModule(), "rpc-test");
        ContainerLike rpcInputSchemaNode = null;
        for (final RpcDefinition rpc : rpcModule.getRpcs()) {
            if (rpcQName.isEqualWithoutRevision(rpc.getQName())) {
                rpcInputSchemaNode = rpc.getInput();
                break;
            }
        }
        assertNotNull(rpcInputSchemaNode);

        final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> container =
                SchemaAwareBuilders.containerBuilder(rpcInputSchemaNode);

        final QName contQName = QName.create(rpcModule.getQNameModule(), "cont");
        final DataSchemaNode contSchemaNode = rpcInputSchemaNode.getDataChildByName(contQName);
        assertTrue(contSchemaNode instanceof ContainerSchemaNode);
        final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> contNode =
                SchemaAwareBuilders.containerBuilder((ContainerSchemaNode) contSchemaNode);

        final QName lfQName = QName.create(rpcModule.getQNameModule(), "lf");
        final DataSchemaNode lfSchemaNode = ((ContainerSchemaNode) contSchemaNode).getDataChildByName(lfQName);
        assertTrue(lfSchemaNode instanceof LeafSchemaNode);
        final LeafNode<Object> lfNode =
                SchemaAwareBuilders.leafBuilder((LeafSchemaNode) lfSchemaNode).withValue("any value").build();
        contNode.withChild(lfNode);
        container.withChild(contNode.build());

        return new NormalizedNodeContext(
                new InstanceIdentifierContext<>(null, rpcInputSchemaNode, null, schema), container.build());
    }

    @Test
    public void testInvokeRpcWithNoPayloadRpc_FailNoErrors() {
        final QName qname = QName.create("(http://netconfcentral.org/ns/toaster?revision=2009-11-20)cancel-toast");

        doReturn(immediateFailedFluentFuture(new DOMRpcImplementationNotAvailableException("testExeption")))
            .when(brokerFacade).invokeRpc(eq(qname), any());

        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> this.restconfImpl.invokeRpc("toaster:cancel-toast", null, uriInfo));
        verifyRestconfDocumentedException(ex, 0, ErrorType.APPLICATION, ErrorTag.OPERATION_NOT_SUPPORTED,
            Optional.empty(), Optional.empty());
    }

    void verifyRestconfDocumentedException(final RestconfDocumentedException restDocumentedException, final int index,
            final ErrorType expErrorType, final ErrorTag expErrorTag, final Optional<String> expErrorMsg,
            final Optional<String> expAppTag) {

        final List<RestconfError> errors = restDocumentedException.getErrors();
        assertTrue("RestconfError not found at index " + index, errors.size() > index);

        RestconfError actual = errors.get(index);

        assertEquals("getErrorType", expErrorType, actual.getErrorType());
        assertEquals("getErrorTag", expErrorTag, actual.getErrorTag());
        assertNotNull("getErrorMessage is null", actual.getErrorMessage());

        if (expErrorMsg.isPresent()) {
            assertEquals("getErrorMessage", expErrorMsg.get(), actual.getErrorMessage());
        }

        if (expAppTag.isPresent()) {
            assertEquals("getErrorAppTag", expAppTag.get(), actual.getErrorAppTag());
        }
    }

    @Test
    public void testInvokeRpcWithNoPayloadRpc_FailWithRpcError() {
        final List<RpcError> rpcErrors = Arrays.asList(
                RpcResultBuilder.newError(RpcError.ErrorType.TRANSPORT, "bogusTag", "foo"),
                RpcResultBuilder.newWarning(RpcError.ErrorType.RPC, "in-use", "bar",
                        "app-tag", null, null));

        final DOMRpcResult result = new DefaultDOMRpcResult(rpcErrors);
        final QName path = QName.create("(http://netconfcentral.org/ns/toaster?revision=2009-11-20)cancel-toast");
        doReturn(immediateFluentFuture(result)).when(brokerFacade).invokeRpc(eq(path), any());

        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> this.restconfImpl.invokeRpc("toaster:cancel-toast", null, uriInfo));

        // We are performing pass-through here of error-tag, hence the tag remains as specified, but we want to make
        // sure the HTTP status remains the same as
        final ErrorTag bogus = new ErrorTag("bogusTag");
        verifyRestconfDocumentedException(ex, 0, ErrorType.TRANSPORT, bogus, Optional.of("foo"), Optional.empty());
        assertEquals(ErrorTags.statusOf(ErrorTag.OPERATION_FAILED), ErrorTags.statusOf(bogus));

        verifyRestconfDocumentedException(ex, 1, ErrorType.RPC, ErrorTag.IN_USE, Optional.of("bar"),
            Optional.of("app-tag"));
    }

    @Test
    public void testInvokeRpcWithNoPayload_Success() {
        final NormalizedNode resultObj = null;
        final DOMRpcResult expResult = new DefaultDOMRpcResult(resultObj);

        final QName qname = QName.create("(http://netconfcentral.org/ns/toaster?revision=2009-11-20)cancel-toast");

        doReturn(immediateFluentFuture(expResult)).when(brokerFacade).invokeRpc(eq(qname), any());

        final NormalizedNodeContext output = this.restconfImpl.invokeRpc("toaster:cancel-toast", null, uriInfo);
        assertNotNull(output);
        assertEquals(null, output.getData());
        // additional validation in the fact that the restconfImpl does not
        // throw an exception.
    }

    @Test
    public void testInvokeRpcWithEmptyOutput() {
        final ContainerNode resultObj = mock(ContainerNode.class);
        doReturn(Set.of()).when(resultObj).body();
        doCallRealMethod().when(resultObj).isEmpty();
        final DOMRpcResult expResult = new DefaultDOMRpcResult(resultObj);

        final QName qname = QName.create("(http://netconfcentral.org/ns/toaster?revision=2009-11-20)cancel-toast");
        doReturn(immediateFluentFuture(expResult)).when(brokerFacade).invokeRpc(eq(qname), any());

        WebApplicationException exceptionToBeThrown = assertThrows(WebApplicationException.class,
            () -> this.restconfImpl.invokeRpc("toaster:cancel-toast", null, uriInfo));
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), exceptionToBeThrown.getResponse().getStatus());
    }

    @Test
    public void testInvokeRpcMethodWithBadMethodName() {
        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> this.restconfImpl.invokeRpc("toaster:bad-method", null, uriInfo));
        verifyRestconfDocumentedException(ex, 0, ErrorType.RPC, ErrorTag.UNKNOWN_ELEMENT,
            Optional.empty(), Optional.empty());
    }

    @Test
    @Ignore
    public void testInvokeRpcMethodWithInput() {
        final DOMRpcResult expResult = mock(DOMRpcResult.class);
        final QName path = QName.create("(http://netconfcentral.org/ns/toaster?revision=2009-11-20)make-toast");

        final Module rpcModule = schemaContext.findModules("toaster").iterator().next();
        assertNotNull(rpcModule);
        final QName rpcQName = QName.create(rpcModule.getQNameModule(), "make-toast");

        RpcDefinition rpcDef = null;
        ContainerLike rpcInputSchemaNode = null;
        for (final RpcDefinition rpc : rpcModule.getRpcs()) {
            if (rpcQName.isEqualWithoutRevision(rpc.getQName())) {
                rpcInputSchemaNode = rpc.getInput();
                rpcDef = rpc;
                break;
            }
        }

        assertNotNull(rpcDef);
        assertNotNull(rpcInputSchemaNode);
        final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> containerBuilder =
                SchemaAwareBuilders.containerBuilder(rpcInputSchemaNode);

        final NormalizedNodeContext payload =
                new NormalizedNodeContext(new InstanceIdentifierContext<>(null, rpcInputSchemaNode,
                null, schemaContext), containerBuilder.build());

        doReturn(immediateFluentFuture(expResult)).when(brokerFacade).invokeRpc(eq(path), any(NormalizedNode.class));

        final NormalizedNodeContext output = this.restconfImpl.invokeRpc("toaster:make-toast", payload, uriInfo);
        assertNotNull(output);
        assertEquals(null, output.getData());
        // additional validation in the fact that the restconfImpl does not
        // throw an exception.
    }

    @Test
    public void testThrowExceptionWhenSlashInModuleName() {
        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> this.restconfImpl.invokeRpc("toaster/slash", null, uriInfo));
        verifyRestconfDocumentedException(ex, 0, ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
            Optional.empty(), Optional.empty());
    }

    @Test
    public void testInvokeRpcWithNoPayloadWithOutput_Success() {
        final SchemaContext schema = controllerContext.getGlobalSchema();
        final Module rpcModule = schema.findModules("toaster").iterator().next();
        assertNotNull(rpcModule);
        final QName rpcQName = QName.create(rpcModule.getQNameModule(), "testOutput");
        final QName rpcOutputQName = QName.create(rpcModule.getQNameModule(),"output");

        RpcDefinition rpcDef = null;
        ContainerLike rpcOutputSchemaNode = null;
        for (final RpcDefinition rpc : rpcModule.getRpcs()) {
            if (rpcQName.isEqualWithoutRevision(rpc.getQName())) {
                rpcOutputSchemaNode = rpc.getOutput();
                rpcDef = rpc;
                break;
            }
        }
        assertNotNull(rpcDef);
        assertNotNull(rpcOutputSchemaNode);
        final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> containerBuilder =
                SchemaAwareBuilders.containerBuilder(rpcOutputSchemaNode);
        final DataSchemaNode leafSchema = rpcOutputSchemaNode
                .getDataChildByName(QName.create(rpcModule.getQNameModule(), "textOut"));
        assertTrue(leafSchema instanceof LeafSchemaNode);
        final NormalizedNodeBuilder<NodeIdentifier, Object, LeafNode<Object>> leafBuilder =
                SchemaAwareBuilders.leafBuilder((LeafSchemaNode) leafSchema);
        leafBuilder.withValue("brm");
        containerBuilder.withChild(leafBuilder.build());
        final ContainerNode container = containerBuilder.build();

        final DOMRpcResult result = new DefaultDOMRpcResult(container);

        doReturn(immediateFluentFuture(result)).when(brokerFacade).invokeRpc(eq(rpcDef.getQName()), any());

        final NormalizedNodeContext output = this.restconfImpl.invokeRpc("toaster:testOutput", null, uriInfo);
        assertNotNull(output);
        assertNotNull(output.getData());
        assertSame(container, output.getData());
        assertNotNull(output.getInstanceIdentifierContext());
        assertNotNull(output.getInstanceIdentifierContext().getSchemaContext());
    }

    /**
     * Tests calling of RestConfImpl method invokeRpc. In the method there is searched rpc in remote schema context.
     * This rpc is then executed.
     * I wasn't able to simulate calling of rpc on remote device therefore this testing method raise method when rpc is
     * invoked.
     */
    @Test
    public void testMountedRpcCallNoPayload_Success() throws Exception {
        // FIXME find how to use mockito for it
    }
}
