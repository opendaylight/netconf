/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.yang.gen.v1.module._1.rev140101.Module1Data;
import org.opendaylight.yang.gen.v1.module._2.rev140102.Module2Data;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

@ExtendWith(MockitoExtension.class)
class RestconfOperationsGetTest extends AbstractRestconfTest {
    private static final ApiPath DEVICE_ID =
        apiPath("network-topology:network-topology/topology=topology-netconf/node=device/yang-ext:mount");
    private static final ApiPath DEVICE_RPC1_MODULE1_ID = apiPath("network-topology:network-topology/"
        + "topology=topology-netconf/node=device/yang-ext:mount/module1:dummy-rpc1-module1");
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

    private static final EffectiveModelContext MODEL_CONTEXT = BindingRuntimeHelpers.createRuntimeContext(
        Module1Data.class, Module2Data.class, NetworkTopology.class).modelContext();

    @Override
    EffectiveModelContext modelContext() {
        return MODEL_CONTEXT;
    }

    @Test
    void testOperationsJson() {
        assertEquals(EXPECTED_JSON, assertEntity(200, ar -> restconf.operationsJsonGET(ar)));
    }

    @Test
    void testOperationsXml() {
        assertEquals(EXPECTED_XML, assertEntity(200, ar -> restconf.operationsXmlGET(ar)));
    }

    private void mockMountPoint() {
        doReturn(Optional.of(new FixedDOMSchemaService(MODEL_CONTEXT))).when(mountPoint)
            .getService(DOMSchemaService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMRpcService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMActionService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMMountPointService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(NetconfDataTreeService.class);
        doReturn(Optional.of(dataBroker)).when(mountPoint).getService(DOMDataBroker.class);
        doReturn(Optional.of(mountPoint)).when(mountPointService).getMountPoint(any());
    }

    @Test
    void testMountPointOperationsJson() {
        mockMountPoint();
        assertEquals(EXPECTED_JSON, assertEntity(200, ar -> restconf.operationsJsonGET(DEVICE_ID, ar)));
    }

    @Test
    void testMountPointOperationsXml() {
        mockMountPoint();
        assertEquals(EXPECTED_XML, assertEntity(200, ar -> restconf.operationsXmlGET(DEVICE_ID, ar)));
    }

    @Test
    void testMountPointSpecificOperationsJson() {
        mockMountPoint();
        assertEquals("""
            { "module1:dummy-rpc1-module1" : [null] }""",
            assertEntity(200, ar -> restconf.operationsJsonGET(DEVICE_RPC1_MODULE1_ID, ar)));
    }

    @Test
    void testMountPointSpecificOperationsXml() {
        mockMountPoint();
        assertEquals("""
            <dummy-rpc1-module1 xmlns="module:1"/>""",
            assertEntity(200, ar -> restconf.operationsXmlGET(DEVICE_RPC1_MODULE1_ID, ar)));
    }
}
