/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Lists;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceConnection;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;

@ExtendWith(MockitoExtension.class)
class SchemalessNetconfDeviceTest extends AbstractBaseSchemasTest {
    private static final String TEST_NAMESPACE = "test:namespace";
    private static final String TEST_MODULE = "test-module";
    private static final String TEST_REVISION = "2013-07-22";

    @Mock
    private RemoteDeviceConnection connection;
    @Mock
    private RemoteDeviceHandler remoteDeviceHandler;
    @Mock
    private NetconfDeviceCommunicator listener;
    @Mock
    private NetconfMessage netconfMessage;

    @Test
    void testSessionOnMethods() throws Exception {
        doNothing().when(remoteDeviceHandler).close();

        doReturn(connection).when(remoteDeviceHandler).onDeviceConnected(
                any(NetconfDeviceSchema.class), any(NetconfSessionPreferences.class), any(RemoteDeviceServices.class));
        doNothing().when(connection).onNotification(any(DOMNotification.class));

        final var remoteDeviceId = new RemoteDeviceId("test-D", InetSocketAddress.createUnresolved("localhost", 22));

        final var device = new SchemalessNetconfDevice(BASE_SCHEMAS, remoteDeviceId, remoteDeviceHandler);

        final var sessionCaps = getSessionCaps(true,
                List.of(TEST_NAMESPACE + "?module=" + TEST_MODULE + "&amp;revision=" + TEST_REVISION));

        device.onRemoteSessionUp(sessionCaps, listener);

        verify(remoteDeviceHandler).onDeviceConnected(
                any(NetconfDeviceSchema.class), any(NetconfSessionPreferences.class), any(RemoteDeviceServices.class));

        final var document = XmlUtil.readXmlToDocument("""
            <notification xmlns="urn:ietf:params:xml:ns:netconf:notification:1.0">
              <eventTime>2021-11-11T11:26:16Z</eventTime>
              <netconf-config-change xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-notifications"/>
            </notification>""");
        doReturn(document).when(netconfMessage).getDocument();

        device.onNotification(netconfMessage);
        verify(connection).onNotification(any());

        device.onRemoteSessionDown();
        verify(connection).close();
        verify(remoteDeviceHandler).close();
    }

    private static NetconfSessionPreferences getSessionCaps(final boolean addMonitor,
                                                            final Collection<String> additionalCapabilities) {
        final var capabilities = Lists.newArrayList(CapabilityURN.BASE, CapabilityURN.BASE_1_1);
        if (addMonitor) {
            capabilities.add(NetconfState.QNAME.getNamespace().toString());
        }
        capabilities.addAll(additionalCapabilities);
        return NetconfSessionPreferences.fromStrings(capabilities);
    }
}
