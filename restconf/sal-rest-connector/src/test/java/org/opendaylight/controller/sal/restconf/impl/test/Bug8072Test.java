/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
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

public class Bug8072Test {
    private static final String EXTERNAL_MODULE_NAME = "test-module";
    private static final QName MODULES_QNAME = QName.create("test:module", "2014-01-09", "modules");
    private static final QName MODULE_QNAME = QName.create("test:module", "2014-01-09", "module");
    private static final QName NAME_QNAME = QName.create("test:module", "2014-01-09", "name");
    private static final QName TYPE_QNAME = QName.create("test:module", "2014-01-09", "type");
    private static final QName MODULE_TYPE_QNAME = QName.create("test:module", "2014-01-09", "module-type");

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
    public void testIdentityRefFromExternalModule() throws FileNotFoundException, ReactorException {
        initMountService();
        final InstanceIdentifierContext<?> ctx = CONTROLLER_CONTEXT.toInstanceIdentifier(
                "simple-nodes:users/yang-ext:mount/test-module:modules/module/test-module:module-type/name");

        final Map<QName, Object> keyValues = new HashMap<>();
        keyValues.put(NAME_QNAME, "name");
        keyValues.put(TYPE_QNAME, MODULE_TYPE_QNAME);
        final YangInstanceIdentifier expectedYII = YangInstanceIdentifier.of(MODULES_QNAME).node(MODULE_QNAME)
            .node(new YangInstanceIdentifier.NodeIdentifierWithPredicates(MODULE_QNAME, keyValues));

        assertEquals(expectedYII, ctx.getInstanceIdentifier());
    }

    private void initMountService() throws FileNotFoundException, ReactorException {
        final DOMMountPointService mountService = mock(DOMMountPointService.class);
        CONTROLLER_CONTEXT.setMountService(mountService);
        final BrokerFacade brokerFacade = mock(BrokerFacade.class);
        final RestconfImpl restconfImpl = RestconfImpl.getInstance();
        restconfImpl.setBroker(brokerFacade);
        restconfImpl.setControllerContext(CONTROLLER_CONTEXT);
        final SchemaContext mountPointContext = TestUtils.loadSchemaContext("/full-versions/test-module");
        final DOMMountPoint mountInstance = mock(DOMMountPoint.class);
        when(mountInstance.getSchemaContext()).thenReturn(mountPointContext);
        when(mountService.getMountPoint(any(YangInstanceIdentifier.class))).thenReturn(Optional.of(mountInstance));
    }
}
