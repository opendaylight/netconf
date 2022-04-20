/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.rest.doc.impl;

import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl;
import org.opendaylight.netconf.sal.rest.doc.impl.DefinitionGenerator;
import org.opendaylight.netconf.sal.rest.doc.impl.DefinitionNames;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public final class SwaggerObjectTest {
    private EffectiveModelContext context;

    @Before
    public void setUp() {
        context = YangParserTestUtils.parseYangResourceDirectory("/yang");
    }

    @Test
    public void testConvertToJsonSchema() throws Exception {
        final Optional<? extends Module> module = context.findModule("opflex", Revision.of("2014-05-28"));
        assertTrue("Desired module not found", module.isPresent());
        final DefinitionGenerator generator = new DefinitionGenerator();
        final ObjectNode jsonObject = generator.convertToJsonSchema(module.get(), context,
                new DefinitionNames(), ApiDocServiceImpl.OAversion.V2_0, true);
        Assert.assertNotNull(jsonObject);
    }

    @Test
    public void testActionTypes() throws Exception {
        final Optional<? extends Module> module = context.findModule("action-types");
        assertTrue("Desired module not found", module.isPresent());
        final DefinitionGenerator generator = new DefinitionGenerator();
        final ObjectNode jsonObject = generator.convertToJsonSchema(module.get(), context,
                new DefinitionNames(), ApiDocServiceImpl.OAversion.V2_0, true);
        Assert.assertNotNull(jsonObject);
    }

    @Test
    public void testStringTypes() throws Exception {
        final Optional<? extends Module> module = context.findModule("string-types");
        assertTrue("Desired module not found", module.isPresent());
        final DefinitionGenerator generator = new DefinitionGenerator();
        final ObjectNode jsonObject = generator.convertToJsonSchema(module.get(), context, new DefinitionNames(),
                ApiDocServiceImpl.OAversion.V2_0, true);

        Assert.assertNotNull(jsonObject);
    }
}
