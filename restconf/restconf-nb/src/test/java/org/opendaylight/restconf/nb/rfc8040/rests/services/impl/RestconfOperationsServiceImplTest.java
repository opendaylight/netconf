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

import java.util.Optional;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.yang.gen.v1.module._1.rev140101.Module1Data;
import org.opendaylight.yang.gen.v1.module._2.rev140102.Module2Data;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class RestconfOperationsServiceImplTest {
    private static final String DEVICE_ID = "network-topology:network-topology/topology=topology-netconf/"
        + "node=device/yang-ext:mount";
    private static final String DEVICE_RPC1_MODULE1_ID = DEVICE_ID + "module1:dummy-rpc1-module1";
    private static final String EXPECTED_JSON = """
        {
          "ietf-restconf:operations" : {
            "module1:dummy-rpc1-module1" : [null],
            "module1:dummy-rpc2-module1" : [null],
            "module2:dummy-rpc1-module2" : [null],
            "module2:dummy-rpc2-module2" : [null]
          }
        }""";
    private static final String EXPECTED_XML = """
        <operations xmlns="urn:ietf:params:xml:ns:yang:ietf-restconf">
          <dummy-rpc1-module1 xmlns="module:1"/>
          <dummy-rpc2-module1 xmlns="module:1"/>
          <dummy-rpc1-module2 xmlns="module:2"/>
          <dummy-rpc2-module2 xmlns="module:2"/>
        </operations>""";

    private static EffectiveModelContext SCHEMA;

    @Mock
    private DOMMountPointService mountPointService;
    @Mock
    private DOMMountPoint mountPoint;
    @Mock
    private DOMSchemaService schemaService;
    @Mock
    private DOMDataBroker dataBroker;
    @Mock
    private DOMRpcService rpcService;

    private RestconfOperationsServiceImpl opService;

    @BeforeClass
    public static void beforeClass() {
        SCHEMA = BindingRuntimeHelpers.createRuntimeContext(Module1Data.class, Module2Data.class, NetworkTopology.class)
            .getEffectiveModelContext();
    }

    @Before
    public void before() {
        doReturn(SCHEMA).when(schemaService).getGlobalContext();
        doReturn(Optional.of(schemaService)).when(mountPoint).getService(DOMSchemaService.class);
        doReturn(Optional.of(mountPoint)).when(mountPointService).getMountPoint(any());

        final DatabindProvider databindProvider = () -> DatabindContext.ofModel(SCHEMA);
        opService = new RestconfOperationsServiceImpl(
            new MdsalRestconfServer(databindProvider, dataBroker, rpcService, mountPointService));
    }

    @Test
    public void testOperationsJson() {
        final var operationsJSON = opService.getOperationsJSON();
        assertEquals(EXPECTED_JSON, operationsJSON);
    }

    @Test
    public void testOperationsXml() {
        final var operationsXML = opService.operationsGetXML();
        assertEquals(EXPECTED_XML, operationsXML);
    }

    @Test
    public void testMountPointOperationsJson() {
        final var operationJSON = opService.getOperationJSON(DEVICE_ID);
        assertEquals(EXPECTED_JSON, operationJSON);
    }

    @Test
    public void testMountPointOperationsXml() {
        final var operationXML = opService.operationsGetXML(DEVICE_ID);
        assertEquals(EXPECTED_XML, operationXML);
    }

    @Test
    public void testMountPointSpecificOperationsJson() {
        final var operationJSON = opService.getOperationJSON(DEVICE_RPC1_MODULE1_ID);
        assertEquals("""
            { "module1:dummy-rpc1-module1" : [null] }""", operationJSON);
    }

    @Test
    public void testMountPointSpecificOperationsXml() {
        final var operationXML = opService.operationsGetXML(DEVICE_RPC1_MODULE1_ID);
        assertEquals("""
            <dummy-rpc1-module1 xmlns="module:1"/>""", operationXML);
    }
}
