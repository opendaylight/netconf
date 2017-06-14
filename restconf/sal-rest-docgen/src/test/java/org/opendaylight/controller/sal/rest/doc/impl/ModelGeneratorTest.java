/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.sal.rest.doc.impl;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import java.sql.Date;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netconf.sal.rest.doc.impl.ModelGenerator;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;


public class ModelGeneratorTest {

    private static final String NAMESPACE = "urn:opendaylight:groupbasedpolicy:opflex";
    private static final String STRING_DATE = "2014-05-28";
    private static final Date REVISION = Date.valueOf(STRING_DATE);
    private DocGenTestHelper helper;
    private SchemaContext schemaContext;

    @Before
    public void setUp() throws Exception {
        this.helper = new DocGenTestHelper();
        this.helper.setUp();
        this.schemaContext = this.helper.getSchemaContext();
    }

    @Test
    public void testConvertToJsonSchema() throws Exception {

        Preconditions.checkArgument(this.helper.getModules() != null, "No modules found");

        final ModelGenerator generator = new ModelGenerator();

        for (final Module m : this.helper.getModules()) {
            if (m.getQNameModule().getNamespace().toString().equals(NAMESPACE)
                    && m.getQNameModule().getRevision().equals(REVISION)) {

                final ObjectNode jsonObject = generator.convertToJsonSchema(m, this.schemaContext);
                Assert.assertNotNull(jsonObject);
            }
        }

    }
}
