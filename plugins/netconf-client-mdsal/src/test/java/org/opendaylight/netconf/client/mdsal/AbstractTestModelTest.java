/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public abstract class AbstractTestModelTest extends AbstractBaseSchemasTest {
    protected static EffectiveModelContext SCHEMA_CONTEXT;

    @BeforeClass
    public static final void setupSchemaContext() {
        SCHEMA_CONTEXT = YangParserTestUtils.parseYangResource("/schemas/test-module.yang");
    }

    @AfterClass
    public static final void tearDownSchemaContext() {
        SCHEMA_CONTEXT = null;
    }
}
