/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology.cluster.impl.device;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import akka.actor.ActorContext;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.Executors;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.topology.util.messages.NormalizedNodeMessage;
import org.opendaylight.restconfsb.topology.cluster.impl.messages.RpcMessage;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import scala.Function1;
import scala.concurrent.ExecutionContextExecutor;
import scala.concurrent.Future;
import scala.concurrent.impl.ExecutionContextImpl;
import scala.concurrent.impl.Promise;
import scala.runtime.BoxedUnit;
import scala.util.Success;

public class ProxyRestconfFacadeTest {

    private static final TestData data = new TestData();
    private static final Future<Void> scalaFuture = createScalaVoidFuture();
    private static final Future<Optional<NormalizedNodeMessage>> scalaDataFuture = createScalaDataFuture();
    private static final Future<Optional<RpcMessage>> scalaRpcFuture = createScalaRpcFuture();
    @Mock
    private RestconfFacadeActor master;
    @Mock
    private ActorContext actorSystem;
    @Mock
    private Function1<Throwable, BoxedUnit> reporter;
    private ExecutionContextExecutor ece;
    private ProxyRestconfFacade proxyFacade;

    private static Future<Void> createScalaVoidFuture() {
        final Promise.DefaultPromise<Void> promise = new Promise.DefaultPromise<>();
        return promise.complete(new Success<Void>(null)).future();
    }

    private static Future<Optional<NormalizedNodeMessage>> createScalaDataFuture() {
        final Promise.DefaultPromise<Optional<NormalizedNodeMessage>> promise = new Promise.DefaultPromise<>();
        promise.success(Optional.of(new NormalizedNodeMessage(data.path, data.data)));
        return promise.future();
    }

    private static Future<Optional<RpcMessage>> createScalaRpcFuture() {
        final Promise.DefaultPromise<Optional<RpcMessage>> promise = new Promise.DefaultPromise<>();
        promise.success(Optional.of(new RpcMessage(data.rpcPath, data.rpcResponseContent)));
        return promise.future();
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        ece = new ExecutionContextImpl(Executors.newSingleThreadExecutor(), reporter);
        doReturn(ece).when(actorSystem).dispatcher();
        doReturn(scalaFuture).when(master).headData(LogicalDatastoreType.CONFIGURATION, data.path);
        doReturn(scalaDataFuture).when(master).getData(LogicalDatastoreType.CONFIGURATION, data.path);
        doReturn(scalaFuture).when(master).postConfig(any(NormalizedNodeMessage.class));
        doReturn(scalaFuture).when(master).putConfig(any(NormalizedNodeMessage.class));
        doReturn(scalaFuture).when(master).patchConfig(any(NormalizedNodeMessage.class));
        doReturn(scalaFuture).when(master).deleteConfig(data.path);
        doReturn(scalaRpcFuture).when(master).postOperation(new RpcMessage(data.rpcPath, data.rpcContent));
//        doReturn(scalaRpcFuture).when(master).postOperation(message);
        proxyFacade = new ProxyRestconfFacade(master, actorSystem, null);
    }

    @Test(timeout = 1000)
    public void testHeadData() throws Exception {
        proxyFacade.headData(LogicalDatastoreType.CONFIGURATION, data.path);
        verify(master).headData(LogicalDatastoreType.CONFIGURATION, data.path);
    }

    @Test(timeout = 1000)
    public void testGetData() throws Exception {
        final ListenableFuture<Optional<NormalizedNode<?, ?>>> result =
                proxyFacade.getData(LogicalDatastoreType.CONFIGURATION, ProxyRestconfFacadeTest.data.path);
        final Optional<NormalizedNode<?, ?>> data = result.get();
        Assert.assertTrue(data.isPresent());
        Assert.assertEquals(ProxyRestconfFacadeTest.data.data, data.get());
    }

    @Test(timeout = 1000)
    public void testPostOperation() throws Exception {
        final ListenableFuture<Optional<NormalizedNode<?, ?>>> result = proxyFacade.postOperation(data.rpcPath, data.rpcContent);
        final Optional<NormalizedNode<?, ?>> data = result.get();
        Assert.assertTrue(data.isPresent());
        Assert.assertEquals(ProxyRestconfFacadeTest.data.rpcResponseContent, data.get());
    }

    @Test(timeout = 1000)
    public void testPostConfig() throws Exception {
        proxyFacade.postConfig(data.path, data.data);
        final ArgumentCaptor<NormalizedNodeMessage> captor = ArgumentCaptor.forClass(NormalizedNodeMessage.class);
        verify(master).postConfig(captor.capture());
        Assert.assertEquals(data.path, captor.getValue().getIdentifier());
        Assert.assertEquals(data.data, captor.getValue().getNode());
    }

    @Test(timeout = 1000)
    public void testPutConfig() throws Exception {
        proxyFacade.putConfig(data.path, data.data);
        final ArgumentCaptor<NormalizedNodeMessage> captor = ArgumentCaptor.forClass(NormalizedNodeMessage.class);
        verify(master).putConfig(captor.capture());
        Assert.assertEquals(data.path, captor.getValue().getIdentifier());
        Assert.assertEquals(data.data, captor.getValue().getNode());
    }

    @Test(timeout = 1000)
    public void testPatchConfig() throws Exception {
        proxyFacade.patchConfig(data.path, data.data);
        final ArgumentCaptor<NormalizedNodeMessage> captor = ArgumentCaptor.forClass(NormalizedNodeMessage.class);
        verify(master).patchConfig(captor.capture());
        Assert.assertEquals(data.path, captor.getValue().getIdentifier());
        Assert.assertEquals(data.data, captor.getValue().getNode());
    }

    @Test(timeout = 1000)
    public void testDeleteConfig() throws Exception {
        proxyFacade.deleteConfig(data.path);
        final ArgumentCaptor<YangInstanceIdentifier> captor = ArgumentCaptor.forClass(YangInstanceIdentifier.class);
        verify(master).deleteConfig(captor.capture());
        Assert.assertEquals(data.path, captor.getValue());
    }
}