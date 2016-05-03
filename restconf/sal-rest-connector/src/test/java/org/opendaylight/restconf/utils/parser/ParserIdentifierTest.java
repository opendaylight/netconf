/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.utils.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.md.sal.rest.schema.SchemaExportContext;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class ParserIdentifierTest {
    private static final String TEST_MODULE = "module1/2014-01-01";
    private static final String TEST_MODULE_MOUNT = RestconfConstants.MOUNT + TEST_MODULE;
    private static final String WRONG_IDENTIFIER = "2014-01-01/module1";
    private static final String WRONG_IDENTIFIER_MOUNT = WRONG_IDENTIFIER + RestconfConstants.MOUNT;

    private SchemaContext schemaContext;

    @Before
    public void setup() throws Exception {
        schemaContext = TestRestconfUtils.loadSchemaContext("/modules");
    }

    @Test
    public void toInstanceIdentifierTest() {
        // TODO when method under test will be implmented
    }

    @Test(expected = AbstractMethodError.class)
    public void toInstanceIdentifierNegativeTest() {
        // TODO when method under test will be implmented
        throw new AbstractMethodError("");
    }

    @Test
    public void makeQNameFromIdentifierTest() {
        QName qName = ParserIdentifier.makeQNameFromIdentifier(TEST_MODULE);

        assertNotNull(qName);
        assertEquals("module1", qName.getLocalName());
        assertEquals("2014-01-01", qName.getFormattedRevision());
    }

    @Test(expected = AbstractMethodError.class)
    public void makeQNameFromIdentifierNegativeTest() {
        ParserIdentifier.makeQNameFromIdentifier(WRONG_IDENTIFIER);
    }

    @Test
    public void makeQNameFromIdentifierMountTest() {
        QName qName = ParserIdentifier.makeQNameFromIdentifier(TEST_MODULE_MOUNT);

        assertNotNull(qName);
        assertEquals("module1", qName.getLocalName());
        assertEquals("2014-01-01", qName.getFormattedRevision());
    }

    @Test(expected = AbstractMethodError.class)
    public void makeQNameFromIdentifierMountNegativeTest() {
        ParserIdentifier.makeQNameFromIdentifier(WRONG_IDENTIFIER_MOUNT);
    }

    @Test
    public void toSchemaExportContextFromIdentifierTest() {
        SchemaExportContext exportContext = ParserIdentifier.
                toSchemaExportContextFromIdentifier(schemaContext, TEST_MODULE);
        assertNotNull(exportContext);

        org.opendaylight.yangtools.yang.model.api.Module module = exportContext.getModule();
        assertNotNull(module);

        assertEquals("module1", module.getName());
        assertEquals("2014-01-01", SimpleDateFormatUtil.getRevisionFormat().format(module.getRevision()));
        assertEquals("module:1", module.getNamespace().toString());
    }

    @Test(expected = AbstractMethodError.class)
    public void toSchemaExportContextFromIdentifierNegativeTest() {
        ParserIdentifier.toSchemaExportContextFromIdentifier(schemaContext, WRONG_IDENTIFIER);
    }

    @Test
    public void toSchemaExportContextFromIdentifierMountTest() {
        // TODO when method under test will be implmented
    }

    @Test(expected = AbstractMethodError.class)
    public void toSchemaExportContextFromIdentifierMountNegativeTest() {
        // TODO when method under test will be implmented
        throw new AbstractMethodError("");
    }
}
