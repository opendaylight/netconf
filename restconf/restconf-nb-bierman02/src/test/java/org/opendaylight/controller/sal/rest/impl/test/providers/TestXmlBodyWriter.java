/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.sal.rest.impl.test.providers;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.util.Collection;
import javax.ws.rs.core.MediaType;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.rest.impl.NormalizedNodeXmlBodyWriter;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class TestXmlBodyWriter extends AbstractBodyReaderTest {

    private final NormalizedNodeXmlBodyWriter xmlBodyWriter;
    private static SchemaContext schemaContext;

    public TestXmlBodyWriter() throws NoSuchFieldException, SecurityException {
        this.xmlBodyWriter = new NormalizedNodeXmlBodyWriter();
    }

    @Override
    protected MediaType getMediaType() {
        return new MediaType(MediaType.APPLICATION_XML, null);
    }

    @BeforeClass
    public static void initialization() throws Exception {
        final Collection<File> testFiles = TestRestconfUtils.loadFiles("/instanceidentifier/yang");
        testFiles.addAll(TestRestconfUtils.loadFiles("/invoke-rpc"));
        schemaContext = YangParserTestUtils.parseYangFiles(testFiles);
        CONTROLLER_CONTEXT.setSchemas(schemaContext);
    }

    @Test
    public void rpcModuleInputTest() throws Exception {
        final String uri = "invoke-rpc-module:rpc-test";
        final String pathToInputFile = "/invoke-rpc/xml/rpc-output.xml";
        final NormalizedNodeContext nnContext = TestRestconfUtils
                .loadNormalizedContextFromXmlFile(pathToInputFile, uri);
        final OutputStream output = new ByteArrayOutputStream();
        this.xmlBodyWriter.writeTo(nnContext, null, null, null, this.mediaType, null,
                output);
        assertTrue(output.toString().contains("lf-test"));
    }
}
