/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.refEq;
import static org.mockito.Mockito.doReturn;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.io.InputStream;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.restconfsb.communicator.api.http.HttpException;
import org.opendaylight.restconfsb.communicator.api.http.Request;
import org.opendaylight.restconfsb.communicator.api.http.Sender;
import org.opendaylight.restconfsb.communicator.api.stream.Stream;
import org.opendaylight.restconfsb.communicator.impl.sender.NodeConnectionException;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module;

public class RestconfDeviceInfoTest {

    @Mock
    Sender sender;
    RestconfDeviceInfo restconfDevice;

    private final static RestconfNode restconfNode = new RestconfNodeBuilder().build();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        restconfDevice = new RestconfDeviceInfo(sender);
    }

    @Test
    public void getModulesTest() {
        final ListenableFuture<InputStream> inputStreamModules = inputStreamModules();
        doReturn(inputStreamModules).when(sender).get(refEq(Request.createRequestWithoutBody("/data/ietf-yang-library:modules", Request.RestconfMediaType.XML_DATA)));
        try {
            final List<Module> modules = restconfDevice.getModules(restconfNode);
            assertEquals(2, modules.size());
        } catch (final NodeConnectionException e) {
            e.printStackTrace();
        }
    }

    @Test(expected = NodeConnectionException.class)
    public void getZeroModulesTest() throws NodeConnectionException {
        final ListenableFuture<InputStream> inputStreamModules = inputStreamException();
        doReturn(inputStreamModules).when(sender).get(refEq(Request.createRequestWithoutBody("/data/ietf-yang-library:modules", Request.RestconfMediaType.XML_DATA)));
        final List<Module> streams = restconfDevice.getModules(restconfNode);
        assertEquals(0, streams.size());
    }

    @Test
    public void getStreamsTest() {
        final ListenableFuture<InputStream> inputStreamStreams = inputStreamStreams();
        doReturn(inputStreamStreams).when(sender).get(refEq(Request.createRequestWithoutBody("/data/ietf-restconf-monitoring:restconf-state/streams", Request.RestconfMediaType.XML_DATA)));
        final List<Stream> streams = restconfDevice.getStreams();
        assertEquals(3, streams.size());
    }

    @Test
    public void getZeroStreamsTest() {
        final ListenableFuture<InputStream> inputStreamStreams = inputStreamException();
        doReturn(inputStreamStreams).when(sender).get(refEq(Request.createRequestWithoutBody("/data/ietf-restconf-monitoring:restconf-state/streams", Request.RestconfMediaType.XML_DATA)));
        final List<Stream> streams = restconfDevice.getStreams();
        assertEquals(0, streams.size());
    }

    private ListenableFuture<InputStream> inputStreamException() {
        final SettableFuture<InputStream> is = SettableFuture.create();
        final HttpException httpException = new HttpException(404, "File not found");
        is.setException(httpException);
        return is;
    }

    private ListenableFuture<InputStream> inputStreamModules() {
        final SettableFuture<InputStream> is = SettableFuture.create();
        is.set(this.getClass().getResourceAsStream("/xml/modules.xml"));
        return is;
    }

    private ListenableFuture<InputStream> inputStreamStreams() {
        final SettableFuture<InputStream> is = SettableFuture.create();
        is.set(this.getClass().getResourceAsStream("/xml/streams.xml"));
        return is;
    }

}

