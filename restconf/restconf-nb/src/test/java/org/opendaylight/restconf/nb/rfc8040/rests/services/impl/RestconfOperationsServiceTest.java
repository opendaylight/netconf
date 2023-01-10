/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.Optional;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfOperationsService;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class RestconfOperationsServiceTest {
    private static final String EXPECTED_JSON = """
        {
          "ietf-restconf:operations" : {
            "module1:dummy-rpc1-module1": [null],
            "module1:dummy-rpc2-module1": [null],
            "module2:dummy-rpc1-module2": [null],
            "module2:dummy-rpc2-module2": [null]
          }
        }""";
    private static final String EXPECTED_XML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <operations xmlns="urn:ietf:params:xml:ns:yang:ietf-restconf"
                    xmlns:ns0="module:1"
                    xmlns:ns1="module:2" >
          <ns0:dummy-rpc1-module1/>
          <ns0:dummy-rpc2-module1/>
          <ns1:dummy-rpc1-module2/>
          <ns1:dummy-rpc2-module2/>
        </operations>""";
    private static final String EXPECTED_RPC1_MODULE1_JSON = """
        {
          "ietf-restconf:operations" : {
            "module1:dummy-rpc1-module1": [null]
          }
        }""";
    private static final String EXPECTED_RPC1_MODULE1_XML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <operations xmlns="urn:ietf:params:xml:ns:yang:ietf-restconf"
                    xmlns:ns0="module:1" >
          <ns0:dummy-rpc1-module1/>
        </operations>""";
    private static final String DEVICE_ID = "network-topology:network-topology/topology=topology-netconf/"
        + "node=device/yang-ext:mount";
    private static final String DEVICE_RPC1_MODULE1_ID = "network-topology:network-topology/topology=topology-netconf/"
        + "node=device/yang-ext:mount/module1:dummy-rpc1-module1";

    private static RestconfOperationsService opService;

    @BeforeClass
    public static void startUp() throws Exception {
        final var context = YangParserTestUtils.parseYangFiles(
            TestRestconfUtils.loadFiles("/modules/mount-points-operations"));
        final var mockMountPointService = mock(DOMMountPointService.class);
        final var mockDomMountPoint = mock(DOMMountPoint.class);
        final var mockDomSchemaService = mock(DOMSchemaService.class);
        doReturn(context).when(mockDomSchemaService).getGlobalContext();
        doReturn(Optional.of(mockDomSchemaService)).when(mockDomMountPoint).getService(DOMSchemaService.class);
        doReturn(Optional.of(mockDomMountPoint)).when(mockMountPointService).getMountPoint(any());
        opService = new RestconfOperationsServiceImpl(() -> DatabindContext.ofModel(context), mockMountPointService);
    }

    @Test
    public void testOperationsJson() {
        final var operationsJSON = opService.getOperationsJSON();
        assertEquals(EXPECTED_JSON, operationsJSON);
    }

    @Test
    public void testOperationsXml() {
        final var operationsXML = opService.getOperationsXML();
        assertEquals(EXPECTED_XML, operationsXML);
    }

    @Test
    public void testMountPointOperationsJson() {
        final var operationJSON = opService.getOperationJSON(DEVICE_ID);
        assertEquals(EXPECTED_JSON, operationJSON);
    }

    @Test
    public void testMountPointOperationsXml() {
        final var operationXML = opService.getOperationXML(DEVICE_ID);
        assertEquals(EXPECTED_XML, operationXML);
    }

    @Test
    public void testMountPointSpecificOperationsJson() {
        final var operationJSON = opService.getOperationJSON(DEVICE_RPC1_MODULE1_ID);
        assertEquals(EXPECTED_RPC1_MODULE1_JSON, operationJSON);
    }

    @Test
    public void testMountPointSpecificOperationsXml() {
        final var operationXML = opService.getOperationXML(DEVICE_RPC1_MODULE1_ID);
        assertEquals(EXPECTED_RPC1_MODULE1_XML, operationXML);
    }
}
