/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.impl.osgi;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.doReturn;

import com.google.common.base.Optional;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.config.util.capability.BasicCapability;
import org.opendaylight.controller.config.util.capability.Capability;
import org.opendaylight.controller.config.util.capability.YangModuleCapability;
import org.opendaylight.netconf.api.monitoring.NetconfManagementSession;
import org.opendaylight.netconf.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.opendaylight.netconf.notifications.BaseNotificationPublisherRegistration;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Capabilities;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.CapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.Schema;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.SchemaBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.SessionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfCapabilityChange;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.model.api.Module;

public class NetconfMonitoringServiceImplTest {

    private static final String TEST_MODULE_CONTENT = "content";
    private static final String TEST_MODULE_CONTENT2 = "content2";
    private static final String TEST_MODULE_REV = "1970-01-01";
    private static final String TEST_MODULE_REV2 = "1970-01-02";
    private static final  Uri TEST_MODULE_NAMESPACE = new Uri("testModuleNamespace");
    private static final String TEST_MODULE_NAME = "testModule";
    private static Date TEST_MODULE_DATE;
    private static Date TEST_MODULE_DATE2;

    private YangModuleCapability moduleCapability1;
    private YangModuleCapability moduleCapability2;

    private final Set<Capability> CAPABILITIES = new HashSet<>();

    private NetconfMonitoringServiceImpl monitoringService;
    @Mock
    private Module moduleMock;
    @Mock
    private Module moduleMock2;
    @Mock
    private NetconfOperationServiceFactory operationServiceFactoryMock;
    @Mock
    private NetconfManagementSession sessionMock;

    @BeforeClass
    public static void suiteSetUp() throws Exception {
        TEST_MODULE_DATE = SimpleDateFormatUtil.getRevisionFormat().parse(TEST_MODULE_REV);
        TEST_MODULE_DATE2= SimpleDateFormatUtil.getRevisionFormat().parse(TEST_MODULE_REV2);
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        CAPABILITIES.add(new BasicCapability("urn:ietf:params:netconf:base:1.0"));
        CAPABILITIES.add(new BasicCapability("urn:ietf:params:netconf:base:1.1"));
        CAPABILITIES.add(new BasicCapability("urn:ietf:params:xml:ns:yang:ietf-inet-types?module=ietf-inet-types&amp;revision=2010-09-24"));
        doReturn(new URI(TEST_MODULE_NAMESPACE.getValue())).when(moduleMock).getNamespace();
        doReturn(TEST_MODULE_NAME).when(moduleMock).getName();
        doReturn(TEST_MODULE_DATE).when(moduleMock).getRevision();
        moduleCapability1 = new YangModuleCapability(moduleMock, TEST_MODULE_CONTENT);

        CAPABILITIES.add(moduleCapability1);

        doReturn(new URI(TEST_MODULE_NAMESPACE.getValue())).when(moduleMock2).getNamespace();
        doReturn(TEST_MODULE_NAME).when(moduleMock2).getName();
        doReturn(TEST_MODULE_DATE2).when(moduleMock2).getRevision();
        moduleCapability2 = new YangModuleCapability(moduleMock2, TEST_MODULE_CONTENT2);

        doReturn(CAPABILITIES).when(operationServiceFactoryMock).getCapabilities();
        doReturn(null).when(operationServiceFactoryMock).registerCapabilityListener(any(NetconfMonitoringServiceImpl.class));

        monitoringService = new NetconfMonitoringServiceImpl(operationServiceFactoryMock);
        monitoringService.onCapabilitiesChanged(CAPABILITIES, Collections.emptySet());

        doReturn(new SessionBuilder().build()).when(sessionMock).toManagementSession();
    }

    @Test
    public void testListeners() throws Exception {
        final AtomicInteger stateChanged = new AtomicInteger(0);
        NetconfMonitoringService.MonitoringListener listener = getMonitoringListener(stateChanged);
        monitoringService.registerListener(listener);
        Assert.assertEquals(1, stateChanged.get());
        monitoringService.onSessionUp(sessionMock);
        Assert.assertEquals(2, stateChanged.get());
        HashSet<Capability> added = new HashSet<>();
        added.add(new BasicCapability("toAdd"));
        monitoringService.onCapabilitiesChanged(added, new HashSet<Capability>());
        Assert.assertEquals(3, stateChanged.get());
        monitoringService.onSessionDown(sessionMock);
        Assert.assertEquals(4, stateChanged.get());
    }

    @Test
    public void testGetSchemas() throws Exception {
        Schemas schemas = monitoringService.getSchemas();
        Schema schema = schemas.getSchema().get(0);
        Assert.assertEquals(TEST_MODULE_NAMESPACE, schema.getNamespace());
        Assert.assertEquals(TEST_MODULE_NAME, schema.getIdentifier());
        Assert.assertEquals(TEST_MODULE_REV, schema.getVersion());

    }

    @Test
    public void testGetSchemaForCapability() throws Exception {
        //test multiple revisions of the same capability
        monitoringService.onCapabilitiesChanged(Collections.singleton(moduleCapability2), Collections.emptySet());
        final String schema = monitoringService.getSchemaForCapability(TEST_MODULE_NAME, Optional.of(TEST_MODULE_REV));
        Assert.assertEquals(TEST_MODULE_CONTENT, schema);
        final String schema2 = monitoringService.getSchemaForCapability(TEST_MODULE_NAME, Optional.of(TEST_MODULE_REV2));
        Assert.assertEquals(TEST_MODULE_CONTENT2, schema2);
        //remove one revision
        monitoringService.onCapabilitiesChanged(Collections.emptySet(), Collections.singleton(moduleCapability1));
        //only one revision present
        final String schema3 = monitoringService.getSchemaForCapability(TEST_MODULE_NAME, Optional.absent());
        Assert.assertEquals(TEST_MODULE_CONTENT2, schema3);
    }

    @Test
    public void testGetCapabilities() throws Exception {
        Capabilities actual = monitoringService.getCapabilities();
        List<Uri> exp = new ArrayList<>();
        for (Capability capability : CAPABILITIES) {
            exp.add(new Uri(capability.getCapabilityUri()));
        }
        exp.add(0, new Uri("urn:ietf:params:netconf:capability:candidate:1.0"));
        Capabilities expected = new CapabilitiesBuilder().setCapability(exp).build();
        Assert.assertEquals(new HashSet<>(expected.getCapability()), new HashSet<>(actual.getCapability()));
    }

    @Test
    public void testClose() throws Exception {
        monitoringService.onSessionUp(sessionMock);
        Assert.assertFalse(monitoringService.getSessions().getSession().isEmpty());
        Assert.assertFalse(monitoringService.getCapabilities().getCapability().isEmpty());
        monitoringService.close();
        Assert.assertTrue(monitoringService.getSessions().getSession().isEmpty());
        Assert.assertTrue(monitoringService.getCapabilities().getCapability().isEmpty());
    }

    @Test
    public void testOnCapabilitiesChanged() throws Exception {
        final List<String> actualCapabilities = new ArrayList<>();
        monitoringService.registerListener(new NetconfMonitoringService.MonitoringListener() {
            @Override
            public void onStateChanged(NetconfState state) {
                List<Uri> capability = state.getCapabilities().getCapability();
                for (Uri uri : capability) {
                    actualCapabilities.add(uri.getValue());
                }
            }
        });
        HashSet<Capability> testCaps = new HashSet<>();
        String capUri = "test";
        testCaps.add(new BasicCapability(capUri));
        monitoringService.onCapabilitiesChanged(testCaps, new HashSet<Capability>());
        Assert.assertTrue(actualCapabilities.contains(capUri));
        actualCapabilities.clear();
        monitoringService.onCapabilitiesChanged(new HashSet<Capability>(), testCaps);
        Assert.assertFalse(actualCapabilities.contains(capUri));
    }

    @Test
    public void testonCapabilitiesChanged() throws Exception {
        final String toAdd = "toAdd";
        final String toRemove = "toRemove";
        monitoringService.setNotificationPublisher(new BaseNotificationPublisherRegistration() {
            @Override
            public void onCapabilityChanged(NetconfCapabilityChange capabilityChange) {
                Assert.assertEquals(1, capabilityChange.getAddedCapability().size());

                Assert.assertEquals(toAdd, capabilityChange.getAddedCapability().get(0).getValue());
                Assert.assertEquals(1, capabilityChange.getDeletedCapability().size());
                Assert.assertEquals(toRemove, capabilityChange.getDeletedCapability().get(0).getValue());
            }

            @Override
            public void close() {

            }
        });
        Set<Capability> removed = new HashSet<>();
        removed.add(new BasicCapability(toRemove));
        Set<Capability> added = new HashSet<>();
        added.add(new BasicCapability(toAdd));
        monitoringService.onCapabilitiesChanged(added, removed);
    }

    private NetconfMonitoringService.MonitoringListener getMonitoringListener(final AtomicInteger stateChanged) {
        return new NetconfMonitoringService.MonitoringListener() {
            @Override
            public void onStateChanged(NetconfState state) {
                stateChanged.incrementAndGet();
            }
        };
    }

}