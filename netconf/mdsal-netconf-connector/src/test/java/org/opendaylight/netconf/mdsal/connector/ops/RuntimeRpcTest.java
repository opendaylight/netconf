/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector.ops;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFailedFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;
import com.google.common.util.concurrent.FluentFuture;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
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
import org.opendaylight.netconf.api.DocumentedException.ErrorSeverity;
import org.opendaylight.netconf.api.DocumentedException.ErrorTag;
import org.opendaylight.netconf.api.DocumentedException.ErrorType;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.mapping.api.HandlingPriority;
import org.opendaylight.netconf.mapping.api.NetconfOperationChainedExecution;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.util.test.XmlFileLoader;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.concepts.NoOpListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContextListener;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.OutputSchemaNode;
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
    private static final String SESSION_ID_FOR_REPORTING = "netconf-test-session1";
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
        public FluentFuture<DOMRpcResult> invokeRpc(final QName type, final NormalizedNode<?, ?> input) {
            return immediateFluentFuture(new DefaultDOMRpcResult(null, Collections.emptyList()));
        }

        @Override
        public <T extends DOMRpcAvailabilityListener> ListenerRegistration<T> registerRpcListener(final T listener) {
            return NoOpListenerRegistration.of(listener);
        }
    };

    private static final DOMRpcService RPC_SERVICE_FAILED_INVOCATION = new DOMRpcService() {
        @Override
        public FluentFuture<DOMRpcResult> invokeRpc(final QName type, final NormalizedNode<?, ?> input) {
            return immediateFailedFluentFuture(new DOMRpcException("rpc invocation not implemented yet") {
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
        public FluentFuture<DOMRpcResult> invokeRpc(final QName type, final NormalizedNode<?, ?> input) {
            final Collection<DataContainerChild<? extends PathArgument, ?>> children =
                    ((ContainerNode) input).getValue();
            final Module module = SCHEMA_CONTEXT.findModules(type.getNamespace()).stream()
                .findFirst().orElse(null);
            final RpcDefinition rpcDefinition = getRpcDefinitionFromModule(module, module.getNamespace(),
                type.getLocalName());
            final OutputSchemaNode outputSchemaNode = rpcDefinition.getOutput();
            final ContainerNode node = ImmutableContainerNodeBuilder.create()
                    .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(outputSchemaNode.getQName()))
                    .withValue(children).build();

            return immediateFluentFuture(new DefaultDOMRpcResult(node));
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

        doAnswer(invocationOnMock -> {
            final SourceIdentifier sId = (SourceIdentifier) invocationOnMock.getArguments()[0];
            final YangTextSchemaSource yangTextSchemaSource =
                    YangTextSchemaSource.delegateForByteSource(sId, ByteSource.wrap("module test".getBytes()));
            return immediateFluentFuture(yangTextSchemaSource);
        }).when(sourceProvider).getSource(any(SourceIdentifier.class));

        this.currentSchemaContext = new CurrentSchemaContext(schemaService, sourceProvider);
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

        try {
            rpc.handle(rpcDocument, NetconfOperationChainedExecution.EXECUTION_TERMINATION_POINT);
            fail("should have failed with rpc invocation not implemented yet");
        } catch (final DocumentedException e) {
            assertTrue(e.getErrorType() == ErrorType.APPLICATION);
            assertTrue(e.getErrorSeverity() == ErrorSeverity.ERROR);
            assertTrue(e.getErrorTag() == ErrorTag.OPERATION_FAILED);
        }
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

        try {
            rpc.handle(rpcDocument, NetconfOperationChainedExecution.EXECUTION_TERMINATION_POINT);
            fail("Should have failed, rpc has bad namespace");
        } catch (final DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.ERROR);
            assertTrue(e.getErrorTag() == ErrorTag.BAD_ELEMENT);
            assertTrue(e.getErrorType() == ErrorType.APPLICATION);
        }
    }

    private static void verifyResponse(final Document response, final Document template) {
        final DetailedDiff dd = new DetailedDiff(new Diff(response, template));
        dd.overrideElementQualifier(new RecursiveElementNameAndTextQualifier());
        //we care about order so response has to be identical
        assertTrue(dd.identical());
    }

    private static RpcDefinition getRpcDefinitionFromModule(final Module module, final URI namespaceURI,
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
