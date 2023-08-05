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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Lists;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
import org.w3c.dom.Document;

class SchemalessNetconfDeviceTest extends AbstractBaseSchemasTest {

    private static final String TEST_NAMESPACE = "test:namespace";
    private static final String TEST_MODULE = "test-module";
    private static final String TEST_REVISION = "2013-07-22";

    @Test
    void testSessionOnMethods() throws Exception {
        final RemoteDeviceHandler facade = getFacade();
        final NetconfDeviceCommunicator listener = mockCloseableClass(NetconfDeviceCommunicator.class);
        final RemoteDeviceId remoteDeviceId = new RemoteDeviceId("test-D",
                InetSocketAddress.createUnresolved("localhost", 22));

        final SchemalessNetconfDevice device = new SchemalessNetconfDevice(BASE_SCHEMAS, remoteDeviceId, facade);

        final NetconfSessionPreferences sessionCaps = getSessionCaps(true,
                List.of(TEST_NAMESPACE + "?module=" + TEST_MODULE + "&amp;revision=" + TEST_REVISION));

        device.onRemoteSessionUp(sessionCaps, listener);

        final var connection = verify(facade).onDeviceConnected(
                any(NetconfDeviceSchema.class), any(NetconfSessionPreferences.class), any(RemoteDeviceServices.class));

        final NetconfMessage netconfMessage = mock(NetconfMessage.class);
        final Document document = XmlUtil.readXmlToDocument("""
            <notification xmlns="urn:ietf:params:xml:ns:netconf:notification:1.0">
              <eventTime>2021-11-11T11:26:16Z</eventTime>
              <netconf-config-change xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-notifications"/>
            </notification>""");
        doReturn(document).when(netconfMessage).getDocument();

        device.onNotification(netconfMessage);
        verify(connection).onNotification(any());

        device.onRemoteSessionDown();
        verify(connection).close();
        verify(facade).close();
    }

    private static RemoteDeviceHandler getFacade() throws Exception {
        final var remoteDeviceHandler = mockCloseableClass(RemoteDeviceHandler.class);
        final var connection = mock(RemoteDeviceConnection.class);
        doReturn(connection).when(remoteDeviceHandler).onDeviceConnected(
                any(NetconfDeviceSchema.class), any(NetconfSessionPreferences.class), any(RemoteDeviceServices.class));
        doNothing().when(remoteDeviceHandler).close();
        doNothing().when(connection).onNotification(any(DOMNotification.class));
        return remoteDeviceHandler;
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
        final var capabilities = Lists.newArrayList(CapabilityURN.BASE, CapabilityURN.BASE_1_1);
        if (addMonitor) {
            capabilities.add(NetconfState.QNAME.getNamespace().toString());
        }
        capabilities.addAll(additionalCapabilities);
        return NetconfSessionPreferences.fromStrings(capabilities);
    }
}
