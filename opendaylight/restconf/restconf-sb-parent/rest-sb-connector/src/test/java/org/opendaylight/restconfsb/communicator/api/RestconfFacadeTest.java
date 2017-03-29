/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.api;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.restconfsb.communicator.api.http.HttpException;
import org.opendaylight.restconfsb.communicator.api.http.NotFoundException;
import org.opendaylight.restconfsb.communicator.api.http.Request;
import org.opendaylight.restconfsb.communicator.api.http.Sender;
import org.opendaylight.restconfsb.communicator.api.http.SseListener;
import org.opendaylight.restconfsb.communicator.impl.RestconfFacadeImpl;
import org.opendaylight.restconfsb.communicator.impl.xml.RetestUtils;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;

public class RestconfFacadeTest {

    private static final String RPC_OUTPUT = "<output xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\">" +
            "module yang-mod{}" +
            "</output>";
    private static SchemaContext schemaContext;
    private static final String ERROR_MESSAGE = "<errors xmlns=\"urn:ietf:params:xml:ns:yang:ietf-restconf\">\n" +
            "  <error>\n" +
            "    <error-type>protocol</error-type>\n" +
            "    <error-tag>lock-denied</error-tag>\n" +
            "    <error-message>Lock failed, lock already held</error-message>\n" +
            "  </error>\n" +
            "</errors>";
    public static final String NAMESPACE = "urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring";
    public static final String REVISION = "2010-10-04";
    public static final QName MONITORING_QNAME = QName.create(NAMESPACE, REVISION, "ietf-netconf-monitoring");

    @Mock
    private Sender sender;
    @Mock
    private ListenerRegistration sseListenerReg;

    private static final YangInstanceIdentifier.NodeIdentifierWithPredicates sessionId =
            new YangInstanceIdentifier.NodeIdentifierWithPredicates(QName.create(MONITORING_QNAME, "session"), QName.create(MONITORING_QNAME, "session-id"), 1);
    private static final YangInstanceIdentifier path = YangInstanceIdentifier.builder()
            .node(QName.create(MONITORING_QNAME, "netconf-state"))
            .node(QName.create(MONITORING_QNAME, "sessions"))
            .node(QName.create(MONITORING_QNAME, "session"))
            .node(sessionId)
            .build();
    private RestconfFacade facade;

    @BeforeClass
    public static void suiteSetup() throws ReactorException {
        final List<InputStream> streams = new ArrayList<>();
        streams.add(RestconfFacadeTest.class.getResourceAsStream("/yang/ietf-netconf-monitoring@2010-10-04.yang"));
        streams.add(RestconfFacadeTest.class.getResourceAsStream("/yang/ietf-yang-types@2010-09-24.yang"));
        streams.add(RestconfFacadeTest.class.getResourceAsStream("/yang/ietf-inet-types@2010-09-24.yang"));
        schemaContext = RetestUtils.parseYangStreams(streams);
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(sseListenerReg).when(sender).registerSseListener(any(SseListener.class));
        final InputStream output = new ByteArrayInputStream(RPC_OUTPUT.getBytes());
        doReturn(Futures.immediateFuture(output)).when(sender).post(any(Request.class));
        facade = RestconfFacadeImpl.createXmlRestconfFacade(schemaContext, sender);
    }


    @Test
    public void testGetData() throws Exception {
        final String data = "<session xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\">" +
                "<session-id>1</session-id>" +
                "</session>";
        doReturn(Futures.immediateFuture(new ByteArrayInputStream(data.getBytes()))).when(sender).get(any(Request.class));
        final Optional<NormalizedNode<?, ?>> result = facade.getData(LogicalDatastoreType.CONFIGURATION, path).get();
        Assert.assertTrue(result.isPresent());
        Assert.assertEquals(NAMESPACE, result.get().getIdentifier().getNodeType().getNamespace().toString());
    }

    @Test(expected = ExecutionException.class)
    public void testHeadDataFailed404() throws ExecutionException, InterruptedException {
        final HttpException httpException = new NotFoundException("File not found");
        doReturn(Futures.immediateFailedFuture(httpException)).when(sender).head(any(Request.class));

        try {
            facade.headData(LogicalDatastoreType.CONFIGURATION, path).get();
        } catch (InterruptedException | ExecutionException e) {
            Assert.assertEquals(httpException, e.getCause());
            throw e;
        }
    }

    @Test
    public void testHeadData() throws ExecutionException, InterruptedException {
        final String data = "<session xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\">" +
                "<session-id>1</session-id>" +
                "</session>";

        doReturn(Futures.immediateFuture(new ByteArrayInputStream(data.getBytes()))).when(sender).head(any(Request.class));
        facade.headData(LogicalDatastoreType.CONFIGURATION, path).get();
    }

    @Test
    public void testGetDataNonExist() throws Exception {
        doReturn(Futures.immediateFailedFuture(new NotFoundException("not found"))).when(sender).get(any(Request.class));
        final Optional<NormalizedNode<?, ?>> result = facade.getData(LogicalDatastoreType.CONFIGURATION, path).get();
        Assert.assertFalse(result.isPresent());
    }

    @Test
    public void testPostOperation() throws Exception {
        final QName rpcQname = QName.create(NAMESPACE, REVISION, "get-schema");
        final SchemaPath schemaPath = SchemaPath.create(true, rpcQname);
        final LeafNode identifier = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(rpcQname, "identifier")))
                .withValue("id")
                .build();
        final LeafNode version = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(rpcQname, "version")))
                .withValue("2015-02-28")
                .build();
        final ContainerNode input = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(rpcQname, "input")))
                .withChild(identifier)
                .withChild(version)
                .build();
        final Optional<NormalizedNode<?, ?>> output = facade.postOperation(schemaPath, input).get();
        Assert.assertTrue(output.isPresent());
    }

    @Test
    public void testParseErrors() throws Exception {
        final Collection<RpcError> rpcErrors = facade.parseErrors(new HttpException(409, ERROR_MESSAGE));
        Assert.assertEquals(1, rpcErrors.size());
        final RpcError error = rpcErrors.iterator().next();
        Assert.assertEquals(RpcError.ErrorType.PROTOCOL, error.getErrorType());
        Assert.assertEquals("lock-denied", error.getTag());
        Assert.assertEquals("Lock failed, lock already held", error.getMessage());
    }

    @Test
    public void testParseUnparseableErrors() throws Exception {
        final Collection<RpcError> rpcErrors = facade.parseErrors(new HttpException(500, "Something wrong happened"));
        Assert.assertEquals(1, rpcErrors.size());
        final RpcError error = rpcErrors.iterator().next();
        Assert.assertEquals(RpcError.ErrorType.APPLICATION, error.getErrorType());
        Assert.assertEquals("operation-failed", error.getTag());
        Assert.assertEquals("500: Something wrong happened", error.getMessage());
    }
}