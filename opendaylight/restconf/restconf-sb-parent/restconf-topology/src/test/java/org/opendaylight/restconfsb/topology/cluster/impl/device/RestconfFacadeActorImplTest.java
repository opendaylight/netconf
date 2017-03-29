/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology.cluster.impl.device;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import akka.actor.TypedProps;
import akka.japi.Creator;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.topology.util.messages.NormalizedNodeMessage;
import org.opendaylight.restconfsb.communicator.api.RestconfFacade;
import org.opendaylight.restconfsb.communicator.api.stream.RestconfDeviceStreamListener;
import org.opendaylight.restconfsb.topology.cluster.impl.messages.RpcMessage;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.FiniteDuration;

public class RestconfFacadeActorImplTest {

    private static final TestData data = new TestData();
    private static final FiniteDuration TIMEOUT = new FiniteDuration(5, TimeUnit.SECONDS);
    @Mock
    private RestconfFacade facade;
    private ListenableFuture<Void> voidFuture;

    private RestconfFacadeActor facadeActor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        final ActorSystem system = ActorSystem.apply();
        voidFuture = Futures.immediateFuture(null);
        final ListenableFuture<Optional<MapEntryNode>> dataFuture = Futures.immediateFuture(Optional.of(data.data));
        final ListenableFuture<Optional<ContainerNode>> rpcResultFuture = Futures.immediateFuture(Optional.of(data.rpcResponseContent));
        doNothing().when(facade).registerNotificationListener(any(RestconfDeviceStreamListener.class));
        doReturn(voidFuture).when(facade).headData(LogicalDatastoreType.CONFIGURATION, data.path);
        doReturn(dataFuture).when(facade).getData(LogicalDatastoreType.CONFIGURATION, data.path);
        doReturn(voidFuture).when(facade).putConfig(data.path, data.data);
        doReturn(voidFuture).when(facade).postConfig(data.path, data.data);
        doReturn(voidFuture).when(facade).patchConfig(data.path, data.data);
        doReturn(voidFuture).when(facade).deleteConfig(data.path);
        doReturn(rpcResultFuture).when(facade).postOperation(data.rpcPath, data.rpcContent);
        doReturn(Futures.immediateFailedFuture(data.exc)).when(facade).getData(LogicalDatastoreType.CONFIGURATION, data.failPath);
        doReturn(Futures.immediateFailedFuture(data.exc)).when(facade).putConfig(data.failPath, data.rpcContent);
        facadeActor = TypedActor.get(system).typedActorOf(new TypedProps<>(RestconfFacadeActor.class, new Creator<RestconfFacadeActorImpl>() {
            @Override
            public RestconfFacadeActorImpl create() throws Exception {
                return new RestconfFacadeActorImpl(facade);
            }
        }));
    }

    @Test
    public void testHeadData() throws Exception {
        final Future<Void> voidFuture = facadeActor.headData(LogicalDatastoreType.CONFIGURATION, data.path);
        final Void result = Await.result(voidFuture, TIMEOUT);
        verify(facade).headData(LogicalDatastoreType.CONFIGURATION, data.path);
        Assert.assertEquals(this.voidFuture.get(), result);
    }

    @Test
    public void testGetData() throws Exception {
        final Future<Optional<NormalizedNodeMessage>> dataFuture = facadeActor.getData(LogicalDatastoreType.CONFIGURATION, data.path);
        final Optional<NormalizedNodeMessage> messageResult = Await.result(dataFuture, TIMEOUT);
        verify(facade).getData(LogicalDatastoreType.CONFIGURATION, data.path);
        Assert.assertTrue(messageResult.isPresent());
        Assert.assertEquals(data.data, messageResult.get().getNode());
        Assert.assertEquals(data.path, messageResult.get().getIdentifier());
    }

    @Test
    public void testPostOperation() throws Exception {
        final Future<Optional<RpcMessage>> dataFuture = facadeActor.postOperation(new RpcMessage(data.rpcPath, data.rpcContent));
        final Optional<RpcMessage> messageResult = Await.result(dataFuture, TIMEOUT);
        verify(facade).postOperation(data.rpcPath, data.rpcContent);
        Assert.assertTrue(messageResult.isPresent());
        Assert.assertEquals(data.rpcResponseContent, messageResult.get().getContent());
        Assert.assertEquals(data.rpcPath, messageResult.get().getSchemaPath());
    }

    @Test
    public void testPostConfig() throws Exception {
        final Future<Void> voidFuture = facadeActor.postConfig(new NormalizedNodeMessage(data.path, data.data));
        Await.result(voidFuture, TIMEOUT);
        verify(facade).postConfig(data.path, data.data);
    }

    @Test
    public void testPutConfig() throws Exception {
        final Future<Void> voidFuture = facadeActor.putConfig(new NormalizedNodeMessage(data.path, data.data));
        Await.result(voidFuture, TIMEOUT);
        verify(facade).putConfig(data.path, data.data);
    }

    @Test
    public void testPatchConfig() throws Exception {
        final Future<Void> voidFuture = facadeActor.patchConfig(new NormalizedNodeMessage(data.path, data.data));
        Await.result(voidFuture, TIMEOUT);
        verify(facade).patchConfig(data.path, data.data);
    }

    @Test
    public void testDeleteConfig() throws Exception {
        final Future<Void> voidFuture = facadeActor.deleteConfig(data.path);
        Await.result(voidFuture, TIMEOUT);
        verify(facade).deleteConfig(data.path);
    }

    @Test
    public void testFailure() throws Exception {
        try {
            final Future<Optional<NormalizedNodeMessage>> result = facadeActor.getData(LogicalDatastoreType.CONFIGURATION, data.failPath);
            Await.result(result, TIMEOUT);
            Assert.fail("Expected exception: " + data.exc);
        } catch (final Exception e) {
            Assert.assertEquals(data.exc, e);
        }
        try {
            final Future<Void> result = facadeActor.putConfig(new NormalizedNodeMessage(data.failPath, data.rpcContent));
            Await.result(result, TIMEOUT);
            Assert.fail("Expected exception: " + data.exc);
        } catch (final Exception e) {
            Assert.assertEquals(data.exc, e);
        }
    }
}