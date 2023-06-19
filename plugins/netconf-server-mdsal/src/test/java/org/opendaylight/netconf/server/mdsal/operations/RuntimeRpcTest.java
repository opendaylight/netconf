/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.operations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;

import com.google.common.base.Preconditions;
import com.google.common.io.CharSource;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.io.Serial;
import java.util.Collection;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import org.custommonkey.xmlunit.DetailedDiff;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.examples.RecursiveElementNameAndTextQualifier;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMRpcAvailabilityListener;
import org.opendaylight.mdsal.dom.api.DOMRpcException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.server.api.operations.HandlingPriority;
import org.opendaylight.netconf.server.api.operations.NetconfOperationChainedExecution;
import org.opendaylight.netconf.server.mdsal.CurrentSchemaContext;
import org.opendaylight.netconf.test.util.XmlFileLoader;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.concepts.NoOpListenerRegistration;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContextListener;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class RuntimeRpcTest {
    private static final Logger LOG = LoggerFactory.getLogger(RuntimeRpcTest.class);
    private static final SessionIdType SESSION_ID_FOR_REPORTING = new SessionIdType(Uint32.valueOf(123));
    private static final Document RPC_REPLY_OK = getReplyOk();

    private static Document getReplyOk() {
        try {
            return XmlFileLoader.xmlFileToDocument("messages/mapping/rpcs/runtimerpc-ok-reply.xml");
        } catch (final IOException | SAXException | ParserConfigurationException e) {
            LOG.debug("unable to load rpc reply ok.", e);
            return XmlUtil.newDocument();
        }
    }

    private static final DOMRpcService RPC_SERVICE_VOID_INVOKER = new DOMRpcService() {
        @Override
        public ListenableFuture<DOMRpcResult> invokeRpc(final QName type, final ContainerNode input) {
            return Futures.immediateFuture(new DefaultDOMRpcResult(null, List.of()));
        }

        @Override
        public <T extends DOMRpcAvailabilityListener> ListenerRegistration<T> registerRpcListener(final T listener) {
            return NoOpListenerRegistration.of(listener);
        }
    };

    private static final DOMRpcService RPC_SERVICE_FAILED_INVOCATION = new DOMRpcService() {
        @Override
        public ListenableFuture<DOMRpcResult> invokeRpc(final QName type, final ContainerNode input) {
            return Futures.immediateFailedFuture(new DOMRpcException("rpc invocation not implemented yet") {
                @Serial
                private static final long serialVersionUID = 1L;
            });
        }

        @Override
        public <T extends DOMRpcAvailabilityListener> ListenerRegistration<T> registerRpcListener(final T listener) {
            return NoOpListenerRegistration.of(listener);
        }
    };

    private final DOMRpcService rpcServiceSuccessfulInvocation = new DOMRpcService() {
        @Override
        public ListenableFuture<DOMRpcResult> invokeRpc(final QName type, final ContainerNode input) {
            final Collection<DataContainerChild> children = input.body();
            final Module module = SCHEMA_CONTEXT.findModules(type.getNamespace()).stream()
                .findFirst().orElse(null);
            final RpcDefinition rpcDefinition = getRpcDefinitionFromModule(module, module.getNamespace(),
                type.getLocalName());
            final ContainerNode node = Builders.containerBuilder()
                    .withNodeIdentifier(new NodeIdentifier(rpcDefinition.getOutput().getQName()))
                    .withValue(children)
                    .build();

            return Futures.immediateFuture(new DefaultDOMRpcResult(node));
        }

        @Override
        public <T extends DOMRpcAvailabilityListener> ListenerRegistration<T> registerRpcListener(final T lsnr) {
            return NoOpListenerRegistration.of(lsnr);
        }
    };

    private static EffectiveModelContext SCHEMA_CONTEXT = null;
    private CurrentSchemaContext currentSchemaContext = null;

    @Mock
    private DOMSchemaService schemaService;
    @Mock
    private EffectiveModelContextListener listener;
    @Mock
    private ListenerRegistration<?> registration;
    @Mock
    private SchemaSourceProvider<YangTextSchemaSource> sourceProvider;

    @BeforeClass
    public static void beforeClass() {
        SCHEMA_CONTEXT = YangParserTestUtils.parseYangResource("/yang/mdsal-netconf-rpc-test.yang");
    }

    @AfterClass
    public static void afterClass() {
        SCHEMA_CONTEXT = null;
    }

    @Before
    public void setUp() throws Exception {
        doNothing().when(registration).close();
        doAnswer(invocationOnMock -> {
            ((EffectiveModelContextListener) invocationOnMock.getArguments()[0]).onModelContextUpdated(SCHEMA_CONTEXT);
            return registration;
        }).when(schemaService).registerSchemaContextListener(any(EffectiveModelContextListener.class));

        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setIgnoreAttributeOrder(true);

        doAnswer(invocationOnMock -> Futures.immediateFuture(YangTextSchemaSource.delegateForCharSource(
            (SourceIdentifier) invocationOnMock.getArguments()[0], CharSource.wrap("module test"))))
            .when(sourceProvider).getSource(any(SourceIdentifier.class));

        currentSchemaContext = CurrentSchemaContext.create(schemaService, sourceProvider);
    }

    @After
    public void tearDown() {
        currentSchemaContext.close();
    }

    @Test
    public void testVoidOutputRpc() throws Exception {
        final RuntimeRpc rpc = new RuntimeRpc(SESSION_ID_FOR_REPORTING, currentSchemaContext, RPC_SERVICE_VOID_INVOKER);

        final Document rpcDocument = XmlFileLoader.xmlFileToDocument("messages/mapping/rpcs/rpc-void-output.xml");
        final HandlingPriority priority = rpc.canHandle(rpcDocument);
        Preconditions.checkState(priority != HandlingPriority.CANNOT_HANDLE);

        final Document response = rpc.handle(rpcDocument, NetconfOperationChainedExecution.EXECUTION_TERMINATION_POINT);

        verifyResponse(response, RPC_REPLY_OK);
    }

    @Test
    public void testSuccesfullNonVoidInvocation() throws Exception {
        final RuntimeRpc rpc = new RuntimeRpc(
            SESSION_ID_FOR_REPORTING, currentSchemaContext, rpcServiceSuccessfulInvocation);

        final Document rpcDocument = XmlFileLoader.xmlFileToDocument("messages/mapping/rpcs/rpc-nonvoid.xml");
        final HandlingPriority priority = rpc.canHandle(rpcDocument);
        Preconditions.checkState(priority != HandlingPriority.CANNOT_HANDLE);

        final Document response = rpc.handle(rpcDocument, NetconfOperationChainedExecution.EXECUTION_TERMINATION_POINT);
        verifyResponse(response, XmlFileLoader.xmlFileToDocument("messages/mapping/rpcs/rpc-nonvoid-control.xml"));
    }

    @Test
    public void testSuccesfullContainerInvocation() throws Exception {
        final RuntimeRpc rpc = new RuntimeRpc(
            SESSION_ID_FOR_REPORTING, currentSchemaContext, rpcServiceSuccessfulInvocation);

        final Document rpcDocument = XmlFileLoader.xmlFileToDocument("messages/mapping/rpcs/rpc-container.xml");
        final HandlingPriority priority = rpc.canHandle(rpcDocument);
        Preconditions.checkState(priority != HandlingPriority.CANNOT_HANDLE);

        final Document response = rpc.handle(rpcDocument, NetconfOperationChainedExecution.EXECUTION_TERMINATION_POINT);
        verifyResponse(response, XmlFileLoader.xmlFileToDocument("messages/mapping/rpcs/rpc-container-control.xml"));
    }

    @Test
    public void testFailedInvocation() throws Exception {
        final RuntimeRpc rpc = new RuntimeRpc(
                SESSION_ID_FOR_REPORTING, currentSchemaContext, RPC_SERVICE_FAILED_INVOCATION);

        final Document rpcDocument = XmlFileLoader.xmlFileToDocument("messages/mapping/rpcs/rpc-nonvoid.xml");
        final HandlingPriority priority = rpc.canHandle(rpcDocument);
        Preconditions.checkState(priority != HandlingPriority.CANNOT_HANDLE);

        final DocumentedException e = assertThrows(DocumentedException.class,
                () -> rpc.handle(rpcDocument, NetconfOperationChainedExecution.EXECUTION_TERMINATION_POINT));

        assertEquals(e.getErrorSeverity(), ErrorSeverity.ERROR);
        assertEquals(e.getErrorTag(), ErrorTag.OPERATION_FAILED);
        assertEquals(e.getErrorType(), ErrorType.APPLICATION);
    }

    @Test
    public void testVoidInputOutputRpc() throws Exception {
        final RuntimeRpc rpc = new RuntimeRpc(SESSION_ID_FOR_REPORTING, currentSchemaContext, RPC_SERVICE_VOID_INVOKER);

        final Document rpcDocument = XmlFileLoader.xmlFileToDocument("messages/mapping/rpcs/rpc-void-input-output.xml");
        final HandlingPriority priority = rpc.canHandle(rpcDocument);
        Preconditions.checkState(priority != HandlingPriority.CANNOT_HANDLE);

        final Document response = rpc.handle(rpcDocument, NetconfOperationChainedExecution.EXECUTION_TERMINATION_POINT);

        verifyResponse(response, RPC_REPLY_OK);
    }

    @Test
    public void testBadNamespaceInRpc() throws Exception {
        final RuntimeRpc rpc = new RuntimeRpc(SESSION_ID_FOR_REPORTING, currentSchemaContext, RPC_SERVICE_VOID_INVOKER);
        final Document rpcDocument = XmlFileLoader.xmlFileToDocument("messages/mapping/rpcs/rpc-bad-namespace.xml");

        final DocumentedException e = assertThrows(DocumentedException.class,
                () -> rpc.handle(rpcDocument, NetconfOperationChainedExecution.EXECUTION_TERMINATION_POINT));

        assertEquals(e.getErrorSeverity(), ErrorSeverity.ERROR);
        assertEquals(e.getErrorTag(), ErrorTag.BAD_ELEMENT);
        assertEquals(e.getErrorType(), ErrorType.APPLICATION);
    }

    private static void verifyResponse(final Document response, final Document template) {
        final DetailedDiff dd = new DetailedDiff(new Diff(response, template));
        dd.overrideElementQualifier(new RecursiveElementNameAndTextQualifier());
        //we care about order so response has to be identical
        assertTrue(dd.identical());
    }

    private static RpcDefinition getRpcDefinitionFromModule(final Module module, final XMLNamespace namespaceURI,
            final String name) {
        for (final RpcDefinition rpcDef : module.getRpcs()) {
            if (rpcDef.getQName().getNamespace().equals(namespaceURI)
                    && rpcDef.getQName().getLocalName().equals(name)) {
                return rpcDef;
            }
        }

        return null;
    }
}
