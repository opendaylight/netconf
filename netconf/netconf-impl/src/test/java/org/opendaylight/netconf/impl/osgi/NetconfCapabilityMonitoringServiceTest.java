/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.impl.osgi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.opendaylight.netconf.api.xml.XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_CAPABILITY_CANDIDATE_1_0;
import static org.opendaylight.netconf.api.xml.XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_CAPABILITY_URL_1_0;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.netconf.api.capability.BasicCapability;
import org.opendaylight.netconf.api.capability.Capability;
import org.opendaylight.netconf.api.capability.YangModuleCapability;
import org.opendaylight.netconf.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.opendaylight.netconf.notifications.BaseNotificationPublisherRegistration;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.HostBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Capabilities;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.CapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.Schema;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.SessionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfCapabilityChange;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.model.api.Module;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfCapabilityMonitoringServiceTest {

    private static final String TEST_MODULE_CONTENT = "content";
    private static final String TEST_MODULE_CONTENT2 = "content2";
    private static final String TEST_MODULE_REV = "1970-01-01";
    private static final String TEST_MODULE_REV2 = "1970-01-02";
    private static final Uri TEST_MODULE_NAMESPACE = new Uri("testModuleNamespace");
    private static final String TEST_MODULE_NAME = "testModule";
    private static final Revision  TEST_MODULE_DATE = Revision.of(TEST_MODULE_REV);
    private static final Revision TEST_MODULE_DATE2 = Revision.of(TEST_MODULE_REV2);

    private YangModuleCapability moduleCapability1;
    private YangModuleCapability moduleCapability2;
    private static final Session SESSION = new SessionBuilder()
            .setSessionId(Uint32.valueOf(1))
            .setSourceHost(HostBuilder.getDefaultInstance("0.0.0.0"))
            .setUsername("admin")
            .build();
    private int capabilitiesSize;

    private final Set<Capability> capabilities = new HashSet<>();

    @Mock
    private Module moduleMock;
    @Mock
    private Module moduleMock2;
    @Mock
    private NetconfOperationServiceFactory operationServiceFactoryMock;
    @Mock
    private NetconfMonitoringService.CapabilitiesListener listener;
    @Mock
    private BaseNotificationPublisherRegistration notificationPublisher;

    private NetconfCapabilityMonitoringService monitoringService;

    @Before
    public void setUp() throws Exception {
        doReturn(new URI(TEST_MODULE_NAMESPACE.getValue())).when(moduleMock).getNamespace();
        doReturn(TEST_MODULE_NAME).when(moduleMock).getName();
        doReturn(Optional.of(TEST_MODULE_DATE)).when(moduleMock).getRevision();
        moduleCapability1 = new YangModuleCapability(moduleMock, TEST_MODULE_CONTENT);

        capabilities.add(moduleCapability1);

        doReturn(new URI(TEST_MODULE_NAMESPACE.getValue())).when(moduleMock2).getNamespace();
        doReturn(TEST_MODULE_NAME).when(moduleMock2).getName();
        doReturn(Optional.of(TEST_MODULE_DATE2)).when(moduleMock2).getRevision();
        moduleCapability2 = new YangModuleCapability(moduleMock2, TEST_MODULE_CONTENT2);

        capabilities.add(new BasicCapability("urn:ietf:params:netconf:base:1.0"));
        capabilities.add(new BasicCapability("urn:ietf:params:netconf:base:1.1"));
        capabilities.add(new BasicCapability("urn:ietf:params:xml:ns:yang:ietf-inet-types?module=ietf-inet-types&amp;"
                + "revision=2010-09-24"));

        doReturn(capabilities).when(operationServiceFactoryMock).getCapabilities();
        doReturn(null).when(operationServiceFactoryMock)
                .registerCapabilityListener(any(NetconfCapabilityMonitoringService.class));

        doNothing().when(listener).onCapabilitiesChanged(any());
        doNothing().when(listener).onSchemasChanged(any());

        doNothing().when(notificationPublisher).onCapabilityChanged(any());

        monitoringService = new NetconfCapabilityMonitoringService(operationServiceFactoryMock);
        monitoringService.onCapabilitiesChanged(capabilities, Collections.emptySet());
        monitoringService.setNotificationPublisher(notificationPublisher);
        monitoringService.registerListener(listener);
        capabilitiesSize = monitoringService.getCapabilities().getCapability().size();
    }

    @Test
    public void testListeners() throws Exception {
        HashSet<Capability> added = new HashSet<>();
        added.add(new BasicCapability("toAdd"));
        monitoringService.onCapabilitiesChanged(added, Collections.emptySet());
        //onCapabilitiesChanged and onSchemasChanged are invoked also after listener registration
        verify(listener, times(2)).onCapabilitiesChanged(any());
        verify(listener, times(2)).onSchemasChanged(any());
    }

    @Test
    public void testGetSchemas() throws Exception {
        Schemas schemas = monitoringService.getSchemas();
        Schema schema = schemas.getSchema().values().iterator().next();
        assertEquals(TEST_MODULE_NAMESPACE, schema.getNamespace());
        assertEquals(TEST_MODULE_NAME, schema.getIdentifier());
        assertEquals(TEST_MODULE_REV, schema.getVersion());
    }

    @Test
    public void testGetSchemaForCapability() throws Exception {
        //test multiple revisions of the same capability
        monitoringService.onCapabilitiesChanged(Collections.singleton(moduleCapability2), Collections.emptySet());
        final String schema =
                monitoringService.getSchemaForModuleRevision(TEST_MODULE_NAME, Optional.of(TEST_MODULE_REV));
        assertEquals(TEST_MODULE_CONTENT, schema);
        final String schema2 =
                monitoringService.getSchemaForModuleRevision(TEST_MODULE_NAME, Optional.of(TEST_MODULE_REV2));
        assertEquals(TEST_MODULE_CONTENT2, schema2);
        //remove one revision
        monitoringService.onCapabilitiesChanged(Collections.emptySet(), Collections.singleton(moduleCapability1));
        //only one revision present
        final String schema3 = monitoringService.getSchemaForModuleRevision(TEST_MODULE_NAME, Optional.empty());
        assertEquals(TEST_MODULE_CONTENT2, schema3);
    }

    @Test
    public void testGetCapabilities() throws Exception {
        List<Uri> exp = new ArrayList<>();
        for (Capability capability : capabilities) {
            exp.add(new Uri(capability.getCapabilityUri()));
        }
        //candidate and url capabilities are added by monitoring service automatically
        exp.add(new Uri(URN_IETF_PARAMS_NETCONF_CAPABILITY_CANDIDATE_1_0));
        exp.add(new Uri(URN_IETF_PARAMS_NETCONF_CAPABILITY_URL_1_0));
        Capabilities expected = new CapabilitiesBuilder().setCapability(exp).build();
        Capabilities actual = monitoringService.getCapabilities();
        assertEquals(new HashSet<>(expected.getCapability()), new HashSet<>(actual.getCapability()));
    }

    @Test
    public void testClose() throws Exception {
        assertFalse(monitoringService.getCapabilities().getCapability().isEmpty());
        monitoringService.close();
        assertTrue(monitoringService.getCapabilities().getCapability().isEmpty());
    }

    @Test
    public void testOnCapabilitiesChanged() throws Exception {
        final String capUri = "test";
        final Uri uri = new Uri(capUri);
        final HashSet<Capability> testCaps = new HashSet<>();
        testCaps.add(new BasicCapability(capUri));
        final ArgumentCaptor<NetconfCapabilityChange> capabilityChangeCaptor =
                ArgumentCaptor.forClass(NetconfCapabilityChange.class);
        final ArgumentCaptor<Capabilities> monitoringListenerCaptor = ArgumentCaptor.forClass(Capabilities.class);
        //add capability
        monitoringService.onCapabilitiesChanged(testCaps, Collections.emptySet());
        //remove capability
        monitoringService.onCapabilitiesChanged(Collections.emptySet(), testCaps);

        verify(listener, times(3)).onCapabilitiesChanged(monitoringListenerCaptor.capture());
        verify(notificationPublisher, times(2)).onCapabilityChanged(capabilityChangeCaptor.capture());

        //verify listener calls
        final List<Capabilities> listenerValues = monitoringListenerCaptor.getAllValues();
        final List<Uri> afterRegisterState = listenerValues.get(0).getCapability();
        final List<Uri> afterAddState = listenerValues.get(1).getCapability();
        final List<Uri> afterRemoveState = listenerValues.get(2).getCapability();

        assertEquals(capabilitiesSize, afterRegisterState.size());
        assertEquals(capabilitiesSize + 1, afterAddState.size());
        assertEquals(capabilitiesSize, afterRemoveState.size());
        assertFalse(afterRegisterState.contains(uri));
        assertTrue(afterAddState.contains(uri));
        assertFalse(afterRemoveState.contains(uri));

        //verify notification publication
        final List<NetconfCapabilityChange> publisherValues = capabilityChangeCaptor.getAllValues();
        final NetconfCapabilityChange afterAdd = publisherValues.get(0);
        final NetconfCapabilityChange afterRemove = publisherValues.get(1);

        assertEquals(Collections.singleton(uri), new HashSet<>(afterAdd.getAddedCapability()));
        assertEquals(Collections.emptySet(), new HashSet<>(afterAdd.getDeletedCapability()));
        assertEquals(Collections.singleton(uri), new HashSet<>(afterRemove.getDeletedCapability()));
        assertEquals(Collections.emptySet(), new HashSet<>(afterRemove.getAddedCapability()));
    }
}
