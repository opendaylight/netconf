/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseNetconfSchemas;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.DefaultBaseNetconfSchemas;
import org.opendaylight.yangtools.yang.model.parser.api.YangParserException;
import org.opendaylight.yangtools.yang.parser.impl.YangParserFactoryImpl;

public abstract class AbstractBaseSchemasTest {
    protected static BaseNetconfSchemas BASE_SCHEMAS;

    @BeforeClass
    public static void initBaseSchemas() throws YangParserException {
        BASE_SCHEMAS = new DefaultBaseNetconfSchemas(new YangParserFactoryImpl());
    }

    @AfterClass
    public static void freeBaseSchemas() {
        BASE_SCHEMAS = null;
    }
}
