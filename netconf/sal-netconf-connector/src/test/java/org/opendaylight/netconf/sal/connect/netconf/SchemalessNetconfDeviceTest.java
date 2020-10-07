/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Lists;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.NetconfMessageTransformer;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.SchemalessMessageTransformer;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.rcf8528.data.util.EmptyMountPointContext;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class SchemalessNetconfDeviceTest extends AbstractBaseSchemasTest {

    private static final String TEST_NAMESPACE = "test:namespace";
    private static final String TEST_MODULE = "test-module";
    private static final String TEST_REVISION = "2013-07-22";
    private static NetconfMessageTransformer netconfMessageTransformer;
    private static EffectiveModelContext cfgCtx;
    private static MountPointContext mountPointContext;

    static {
        cfgCtx =
                YangParserTestUtils.parseYangResources(NetconfToRpcRequestTest.class, "/schemas/config-test-rpc.yang",
                        "/schemas/user-notification.yang");
        mountPointContext = new EmptyMountPointContext(cfgCtx);
        netconfMessageTransformer =
                new NetconfMessageTransformer(mountPointContext, true, BASE_SCHEMAS.getBaseSchemaWithNotifications());
    }

    @Test
    public void testSessionOnMethods() throws Exception {
        final RemoteDeviceHandler<NetconfSessionPreferences> facade = getFacade();
        final NetconfDeviceCommunicator listener = mockCloseableClass(NetconfDeviceCommunicator.class);
        final SchemalessMessageTransformer messageTransformer = mock(SchemalessMessageTransformer.class);
        final RemoteDeviceId remoteDeviceId = new RemoteDeviceId("test-D",
                InetSocketAddress.createUnresolved("localhost", 22));
        final Throwable throwable = new Throwable();

        final SchemalessNetconfDevice device = new SchemalessNetconfDevice(BASE_SCHEMAS, remoteDeviceId, facade,
            messageTransformer);

        final NetconfSessionPreferences sessionCaps = getSessionCaps(true,
                Lists.newArrayList(TEST_NAMESPACE + "?module=" + TEST_MODULE + "&amp;revision=" + TEST_REVISION));

        final NetconfMessage netconfMessage = mock(NetconfMessage.class);

        device.onRemoteSessionUp(sessionCaps, listener);
        verify(facade).onDeviceConnected(any(),
                any(MountPointContext.class), any(NetconfSessionPreferences.class), any(DOMRpcService.class));

        device.onNotification(netconfMessage);
        verify(facade).onNotification(anyString(),isNull());

        device.onRemoteSessionDown();
        verify(facade).onDeviceDisconnected(anyString());

        device.onRemoteSessionFailed(throwable);
        verify(facade).onDeviceFailed(any(), eq(throwable));
    }

    @SuppressWarnings("unchecked")
    private  RemoteDeviceHandler<NetconfSessionPreferences> getFacade() throws Exception {
        NetconfMountPointManager netconfMountPointManager = mock(NetconfMountPointManager.class);
        doReturn(netconfMountPointManager).when(netconfMountPointManager)
                .getInstance(any(), any(), any(), any(), any(), any(), anyLong(), anyLong());
        doReturn(netconfMessageTransformer).when(netconfMountPointManager)
                .getNetconfMessageTransformer(any(MountPointContext.class));
        doNothing().when(netconfMountPointManager).updateNetconfMountPointHandler(any(), any(), any(), any());
        doReturn(mountPointContext).when(netconfMountPointManager).getMountPointContextByNodeId(anyString());
        doNothing().when(netconfMountPointManager).onDeviceConnected(anyString(), any(MountPointContext.class),
                any(NetconfSessionPreferences.class), any());
        doNothing().when(netconfMountPointManager).onDeviceDisconnected(any());
        doNothing().when(netconfMountPointManager).onDeviceFailed(any(), any());
        doNothing().when(netconfMountPointManager).onNotification(anyString(), any());
        return netconfMountPointManager;
    }

    private static <T extends AutoCloseable> T mockCloseableClass(
            final Class<T> remoteDeviceHandlerClass) throws Exception {
        final T mock = mockClass(remoteDeviceHandlerClass);
        doNothing().when(mock).close();
        return mock;
    }

    private static <T> T mockClass(final Class<T> remoteDeviceHandlerClass) {
        final T mock = mock(remoteDeviceHandlerClass);
        Mockito.doReturn(remoteDeviceHandlerClass.getSimpleName()).when(mock).toString();
        return mock;
    }

    private static NetconfSessionPreferences getSessionCaps(final boolean addMonitor,
                                                            final Collection<String> additionalCapabilities) {
        final ArrayList<String> capabilities = Lists.newArrayList(
                XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
                XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1);

        if (addMonitor) {
            capabilities.add(NetconfMessageTransformUtil.IETF_NETCONF_MONITORING.getNamespace().toString());
        }

        capabilities.addAll(additionalCapabilities);

        return NetconfSessionPreferences.fromStrings(
                capabilities);
    }
}
