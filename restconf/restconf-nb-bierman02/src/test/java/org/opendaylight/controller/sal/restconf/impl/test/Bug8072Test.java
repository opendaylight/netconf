/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.restconf.impl.test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;

public class Bug8072Test {
    private static final String EXTERNAL_MODULE_NAME = "test-module";
    private static final QName MODULES_QNAME = QName.create("test:module", "2014-01-09", "modules");
    private static final QName MODULE_QNAME = QName.create("test:module", "2014-01-09", "module");
    private static final QName NAME_QNAME = QName.create("test:module", "2014-01-09", "name");
    private static final QName TYPE_QNAME = QName.create("test:module", "2014-01-09", "type");
    private static final QName MODULE_TYPE_QNAME = QName.create("test:module", "2014-01-09", "module-type");

    private static EffectiveModelContext schemaContext;

    private final ControllerContext controllerContext;

    public Bug8072Test() throws FileNotFoundException {
        final EffectiveModelContext mountPointContext = TestUtils.loadSchemaContext("/full-versions/test-module");
        final DOMMountPoint mountInstance = mock(DOMMountPoint.class);
        controllerContext = TestRestconfUtils.newControllerContext(schemaContext, mountInstance);
        doReturn(Optional.of(FixedDOMSchemaService.of(() -> mountPointContext))).when(mountInstance)
            .getService(DOMSchemaService.class);
    }

    @BeforeClass
    public static void init() throws FileNotFoundException, ReactorException {
        schemaContext = TestUtils.loadSchemaContext("/full-versions/yangs");
        assertEquals(0, schemaContext.findModules(EXTERNAL_MODULE_NAME).size());
    }

    @Test
    public void testIdentityRefFromExternalModule() throws FileNotFoundException, ReactorException {
        final InstanceIdentifierContext<?> ctx = controllerContext.toInstanceIdentifier(
                "simple-nodes:users/yang-ext:mount/test-module:modules/module/test-module:module-type/name");

        final Map<QName, Object> keyValues = new HashMap<>();
        keyValues.put(NAME_QNAME, "name");
        keyValues.put(TYPE_QNAME, MODULE_TYPE_QNAME);
        final YangInstanceIdentifier expectedYII = YangInstanceIdentifier.of(MODULES_QNAME).node(MODULE_QNAME)
            .node(NodeIdentifierWithPredicates.of(MODULE_QNAME, keyValues));

        assertEquals(expectedYII, ctx.getInstanceIdentifier());
    }
}
