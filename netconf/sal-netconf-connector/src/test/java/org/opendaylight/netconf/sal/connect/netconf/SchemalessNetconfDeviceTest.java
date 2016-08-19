/*
 * Copyright (c) 2016, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.sun.org.apache.xerces.internal.impl.xs.opti.DefaultDocument;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.exceptions.ExceptionIncludingMockitoWarnings;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceRpc;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.SchemalessMessageTransformer;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaContextFactory;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class SchemalessNetconfDeviceTest {

    private static final String TEST_NAMESPACE = "test:namespace";
    private static final String TEST_MODULE = "test-module";
    private static final String TEST_REVISION = "2013-07-22";

    @Before
    public void setup(){
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSessionOnMethods() throws Exception {
        final RemoteDeviceHandler<NetconfSessionPreferences> facade = getFacade();
        final NetconfDeviceCommunicator listener = mockCloseableClass(NetconfDeviceCommunicator.class);
        final RemoteDeviceId remoteDeviceId = new RemoteDeviceId("test-D", InetSocketAddress.createUnresolved("localhost", 22));
        final Throwable throwable = new Throwable();

        final SchemalessNetconfDevice device = new SchemalessNetconfDevice(remoteDeviceId, facade);

        final NetconfSessionPreferences sessionCaps = getSessionCaps(true,
                Lists.newArrayList(TEST_NAMESPACE + "?module=" + TEST_MODULE + "&amp;revision=" + TEST_REVISION));

        SchemalessMessageTransformer messageTransformer = mock(SchemalessMessageTransformer.class);
        NetconfMessage netconfMessage = mock(NetconfMessage.class);

        Field f = SchemalessNetconfDevice.class.getDeclaredField("messageTransformer");
        f.setAccessible(true);
        f.set(device, messageTransformer);

        device.onRemoteSessionUp(sessionCaps, listener);
        verify(facade).onDeviceConnected(any(SchemaContext.class), any(NetconfSessionPreferences.class), any(DOMRpcService.class));

        device.onNotification(netconfMessage);
        verify(facade).onNotification(any(DOMNotification.class));

        device.onRemoteSessionDown();
        verify(facade).onDeviceDisconnected();

        device.onRemoteSessionFailed(throwable);
        verify(facade).onDeviceFailed(throwable);
    }

    private RemoteDeviceHandler<NetconfSessionPreferences> getFacade() throws Exception {
        final RemoteDeviceHandler<NetconfSessionPreferences> remoteDeviceHandler = mockCloseableClass(RemoteDeviceHandler.class);
        doNothing().when(remoteDeviceHandler).onDeviceConnected(any(SchemaContext.class), any(NetconfSessionPreferences.class), any(NetconfDeviceRpc.class));
        doNothing().when(remoteDeviceHandler).onDeviceDisconnected();
        doNothing().when(remoteDeviceHandler).onNotification(any(DOMNotification.class));
        return remoteDeviceHandler;
    }

    private <T extends AutoCloseable> T mockCloseableClass(final Class<T> remoteDeviceHandlerClass) throws Exception {
        final T mock = mockClass(remoteDeviceHandlerClass);
        doNothing().when(mock).close();
        return mock;
    }

    private static <T> T mockClass(final Class<T> remoteDeviceHandlerClass) {
        final T mock = mock(remoteDeviceHandlerClass);
        Mockito.doReturn(remoteDeviceHandlerClass.getSimpleName()).when(mock).toString();
        return mock;
    }

    public NetconfSessionPreferences getSessionCaps(final boolean addMonitor, final Collection<String> additionalCapabilities) {
        final ArrayList<String> capabilities = Lists.newArrayList(
                XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
                XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1);

        if(addMonitor) {
            capabilities.add(NetconfMessageTransformUtil.IETF_NETCONF_MONITORING.getNamespace().toString());
        }

        capabilities.addAll(additionalCapabilities);

        return NetconfSessionPreferences.fromStrings(
                capabilities);
    }
}
