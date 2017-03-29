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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.restconfsb.communicator.api.RestconfFacade;
import org.opendaylight.restconfsb.communicator.api.http.NotFoundException;
import org.opendaylight.restconfsb.communicator.api.http.Request;
import org.opendaylight.restconfsb.communicator.api.http.Sender;
import org.opendaylight.restconfsb.communicator.api.http.SseListener;
import org.opendaylight.restconfsb.communicator.api.stream.RestconfDeviceStreamListener;
import org.opendaylight.restconfsb.mountpoint.RestconfDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.YangIdentifier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RevisionIdentifier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.ModuleBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class ClusteredRestconfDeviceTest {

    private static final RestconfDeviceId id = new RestconfDeviceId("node1");
    private static final List<Module> modules = new ArrayList<>();
    private static final File CACHE = new File("cache/schema/");

    @BeforeClass
    public static void suiteSetUp() throws IOException {
        final Module module = new ModuleBuilder()
                .setNamespace(new Uri("urn:dummy:mod-0"))
                .setName(new YangIdentifier("module-0"))
                .setRevision(new RevisionIdentifier("2016-03-01"))
                .build();
        modules.add(module);
        final URL inputUrl = ClusteredRestconfDeviceTest.class.getResource("/yang/module-0@2016-03-01.yang");
        CACHE.mkdirs();
        FileUtils.copyURLToFile(inputUrl, new File(CACHE, "module-0@2016-03-01.yang"));
    }

    @Test
    public void testCreateSlaveDevice() throws Exception {
        final SchemaPath rpc = SchemaPath.create(true, QName.create("urn:dummy:mod-0", "2016-03-01", "rpc1"));
        final RestconfFacade facade = mock(RestconfFacade.class);
        doReturn(Futures.immediateFuture(Optional.absent())).when(facade).postOperation(rpc, null);
        doNothing().when(facade).registerNotificationListener(any(RestconfDeviceStreamListener.class));
        doNothing().when(facade).close();
        final ClusteredRestconfDevice device = ClusteredRestconfDevice.createSlaveDevice(id, facade, modules);
        final CheckedFuture<DOMRpcResult, DOMRpcException> result = device.getRpcService().invokeRpc(rpc, null);
        Assert.assertNull(result.get().getResult());
        verify(facade).postOperation(rpc, null);
        Assert.assertEquals(modules, device.getSupportedModules());
        Assert.assertEquals(1, device.getSchemaContext().getModules().size());
        device.close();
        verify(facade).close();
    }

    @Test
    public void testCreateMasterDevice() throws Exception {
        final SchemaPath rpc = SchemaPath.create(true, QName.create("urn:dummy:mod-0", "2016-03-01", "rpc1"));
        final Sender sender = mock(Sender.class);
        final ListenerRegistration registration = mock(ListenerRegistration.class);
        doNothing().when(registration).close();
        doReturn(Futures.immediateFailedFuture(new NotFoundException("Not found"))).when(sender).get(any(Request.class));
        doReturn(registration).when(sender).registerSseListener(any(SseListener.class));
        doReturn(Futures.immediateFuture(null)).when(sender).post(any(Request.class));
        doNothing().when(sender).close();
        final ScheduledThreadPool executor = mock(ScheduledThreadPool.class);
        doReturn(Executors.newSingleThreadScheduledExecutor()).when(executor).getExecutor();
        final ClusteredRestconfDevice device = ClusteredRestconfDevice.createMasterDevice(id, sender, modules, executor);
        final CheckedFuture<DOMRpcResult, DOMRpcException> result = device.getRpcService().invokeRpc(rpc, null);
        Assert.assertNull(result.get().getResult());
        verify(sender).post(Request.createRequestWithoutBody("/operations/module-0:rpc1", Request.RestconfMediaType.XML_OPERATION));
        Assert.assertEquals(modules, device.getSupportedModules());
        Assert.assertEquals(1, device.getSchemaContext().getModules().size());
        device.close();
        verify(sender).close();
    }


    @AfterClass
    public static void suiteTearDown() throws IOException {
        final File cache = new File("cache");
        FileUtils.deleteDirectory(cache);
    }
}