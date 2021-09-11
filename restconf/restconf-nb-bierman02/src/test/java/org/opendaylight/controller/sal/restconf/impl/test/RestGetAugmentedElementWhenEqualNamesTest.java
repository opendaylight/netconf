/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.restconf.impl.test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.FileNotFoundException;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.yangtools.yang.data.api.YangNetconfError;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public class RestGetAugmentedElementWhenEqualNamesTest {
    private static EffectiveModelContext schemaContext;

    private final ControllerContext controllerContext = TestRestconfUtils.newControllerContext(schemaContext);

    @BeforeClass
    public static void init() throws FileNotFoundException {
        schemaContext = TestUtils.loadSchemaContext("/common/augment/yang");
    }

    @Test
    public void augmentedNodesInUri() {
        InstanceIdentifierContext<?> iiWithData =
                controllerContext.toInstanceIdentifier("main:cont/augment-main-a:cont1");
        assertEquals("ns:augment:main:a", iiWithData.getSchemaNode().getQName().getNamespace().toString());
        iiWithData = controllerContext.toInstanceIdentifier("main:cont/augment-main-b:cont1");
        assertEquals("ns:augment:main:b", iiWithData.getSchemaNode().getQName().getNamespace().toString());
    }

    @Test
    public void nodeWithoutNamespaceHasMoreAugments() {
        final List<YangNetconfError> errors = assertThrows(RestconfDocumentedException.class,
            () -> controllerContext.toInstanceIdentifier("main:cont/cont1")).getErrors();
        assertEquals(1, errors.size());
        assertThat(errors.get(0).message(), containsString("is added as augment from more than one module"));
    }
}
