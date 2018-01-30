/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.sal.restconf.impl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.sal.restconf.impl.BrokerFacade;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfImpl;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;

/**
 * Checks if restconf is able to deserialize external leafref.
 *
 * @see <a href="https://jira.opendaylight.org/browse/NETCONF-505">NETCONF-505</a>
 */
public class Netconf505Test {
    private static final String EXTERNAL_MODULE_NAME = "leafref-module";
    private static final QName CONT_QNAME = QName.create("leafref:module", "2014-04-17", "cont");
    private static final QName LIST_QNAME = QName.create("leafref:module", "2014-04-17", "lst-with-lfref-key");
    private static final QName KEY_QNAME = QName.create("leafref:module", "2014-04-17", "lfref-key");

    private static final ControllerContext CONTROLLER_CONTEXT = ControllerContext.getInstance();

    @BeforeClass
    public static void init() throws FileNotFoundException, ReactorException {
        final SchemaContext globalContext = TestUtils.loadSchemaContext("/full-versions/yangs");
        assertNull(globalContext.findModuleByName(EXTERNAL_MODULE_NAME, null));
        final Set<Module> allModules = globalContext.getModules();
        assertNotNull(allModules);
        CONTROLLER_CONTEXT.setSchemas(globalContext);
    }

    @Test
    public void testLeafRefFromExternalModule() throws FileNotFoundException, ReactorException {
        initMountService();
        final InstanceIdentifierContext<?> ctx = CONTROLLER_CONTEXT.toInstanceIdentifier(
            "simple-nodes:users/yang-ext:mount/leafref-module:cont/lst-with-lfref-key/id-string");

        final Map<QName, Object> keyValues = new HashMap<>();
        keyValues.put(KEY_QNAME, "id-string");
        final YangInstanceIdentifier expectedYII = YangInstanceIdentifier.of(CONT_QNAME).node(LIST_QNAME)
            .node(new YangInstanceIdentifier.NodeIdentifierWithPredicates(LIST_QNAME, keyValues));

        assertEquals(expectedYII, ctx.getInstanceIdentifier());
    }

    private void initMountService() throws FileNotFoundException, ReactorException {
        final DOMMountPointService mountService = mock(DOMMountPointService.class);
        CONTROLLER_CONTEXT.setMountService(mountService);
        final BrokerFacade brokerFacade = mock(BrokerFacade.class);
        final RestconfImpl restconfImpl = RestconfImpl.getInstance();
        restconfImpl.setBroker(brokerFacade);
        restconfImpl.setControllerContext(CONTROLLER_CONTEXT);
        final SchemaContext mountPointContext = TestUtils.loadSchemaContext("/leafref/yang");
        final DOMMountPoint mountInstance = mock(DOMMountPoint.class);
        when(mountInstance.getSchemaContext()).thenReturn(mountPointContext);
        when(mountService.getMountPoint(any(YangInstanceIdentifier.class))).thenReturn(Optional.of(mountInstance));
    }
}
