/*
 * Copyright (c) 2020 OpendayLight and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.Lists;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import org.junit.Test;
import org.mockito.Mock;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.broker.DOMMountPointServiceImpl;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfSalFacadeType;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.rcf8528.data.util.EmptyMountPointContext;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class NetconfMountPointManagerTest extends AbstractTestModelTest {

    public static final String TEST_CAPABILITY = "/schemas/config-test-rpc.yang";
    public static final String TEST_CAPABILITY2 = "/schemas/user-notification.yang";

    private final NetconfMountPointManager mountPointManagerService = new NetconfMountPointManager();
    private final DOMMountPointService service =  new DOMMountPointServiceImpl();
    @Mock
    private DataBroker dataBroker;

    private static NetconfSessionPreferences getSessionCaps(final boolean addMonitor,
            final Collection<String> additionalCapabilities) {
        final ArrayList<String>
                capabilities =
                Lists.newArrayList(XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
                        XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1);

        if (addMonitor) {
            capabilities.add(NetconfMessageTransformUtil.IETF_NETCONF_MONITORING.getNamespace().toString());
        }

        capabilities.addAll(additionalCapabilities);

        return NetconfSessionPreferences.fromStrings(capabilities);
    }

    @Test
    public void testSingleMountPointContextForMultipleSameTypeDevice() {
        NetconfSessionPreferences sessionCaps11 = getSessionCaps(true, Lists.newArrayList(TEST_CAPABILITY));
        MountPointContext
                mountPointContext11 =
                new EmptyMountPointContext(YangParserTestUtils.parseYangResources(this.getClass(), TEST_CAPABILITY));

        NetconfSessionPreferences sessionCaps12 = getSessionCaps(true, Lists.newArrayList(TEST_CAPABILITY));
        MountPointContext
                 mountPointContext12 =
                new EmptyMountPointContext(YangParserTestUtils.parseYangResources(this.getClass(), TEST_CAPABILITY));
        // Managing same type devices two times
        createRemoteDeviceHandlerAndUpdateMountPointForDevice(mountPointContext11, sessionCaps11);
        createRemoteDeviceHandlerAndUpdateMountPointForDevice(mountPointContext12, sessionCaps12);

        // MountPointManager should contains only one MountPointInstance
        assertEquals(1, mountPointManagerService.getCountMountPointInstance());
    }

    @Test
    public void testMountPointContextCountForMultipleSameAndDifferentTypeDevice() {
        NetconfSessionPreferences sessionCaps11 = getSessionCaps(true, Lists.newArrayList(TEST_CAPABILITY));
        MountPointContext
                mountPointContext11 =
                new EmptyMountPointContext(YangParserTestUtils.parseYangResources(this.getClass(), TEST_CAPABILITY));

        NetconfSessionPreferences sessionCaps12 = getSessionCaps(true, Lists.newArrayList(TEST_CAPABILITY));
        MountPointContext
                mountPointContext12 =
                new EmptyMountPointContext(YangParserTestUtils.parseYangResources(this.getClass(), TEST_CAPABILITY));

        NetconfSessionPreferences sessionCaps21 = getSessionCaps(true, Lists.newArrayList(TEST_CAPABILITY2));

        MountPointContext
                mountPointContext21 =
                new EmptyMountPointContext(YangParserTestUtils.parseYangResources(this.getClass(), TEST_CAPABILITY2));
        // Managing two same types (capabilities) devices
        createRemoteDeviceHandlerAndUpdateMountPointForDevice(mountPointContext11, sessionCaps11);
        createRemoteDeviceHandlerAndUpdateMountPointForDevice(mountPointContext12, sessionCaps12);
        // Managing one different type device
        createRemoteDeviceHandlerAndUpdateMountPointForDevice(mountPointContext21, sessionCaps21);

        assertEquals(2, mountPointManagerService.getCountMountPointInstance());
    }

    private void createRemoteDeviceHandlerAndUpdateMountPointForDevice(MountPointContext mountPointContext,
            NetconfSessionPreferences netconfSessionPreferences) {
        RemoteDeviceId remoteDeviceId = getUniqueDeviceId();
        mountPointManagerService.getInstance(remoteDeviceId, service, dataBroker, "topology-id",
                NetconfSalFacadeType.NETCONFDEVICESALFACADE, null, null, null);
        mountPointManagerService.updateNetconfMountPointHandler(remoteDeviceId.getName(), mountPointContext,
                netconfSessionPreferences, null);

    }

    private RemoteDeviceId getUniqueDeviceId() {
        return new RemoteDeviceId(UUID.randomUUID().toString(), InetSocketAddress.createUnresolved("localhost", 22));
    }
}
