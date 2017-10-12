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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import javax.ws.rs.core.MediaType;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.rest.impl.JsonNormalizedNodeBodyReader;
import org.opendaylight.netconf.sal.rest.impl.NormalizedNodeJsonBodyWriter;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class TestJsonBodyWriter extends AbstractBodyReaderTest {

    private final JsonNormalizedNodeBodyReader jsonBodyReader;
    private final NormalizedNodeJsonBodyWriter jsonBodyWriter;
    private static SchemaContext schemaContext;

    public TestJsonBodyWriter() throws NoSuchFieldException, SecurityException {
        super();
        this.jsonBodyWriter = new NormalizedNodeJsonBodyWriter();
        this.jsonBodyReader = new JsonNormalizedNodeBodyReader();
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
        mockBodyReader(uri, this.jsonBodyReader, true);
        final InputStream inputStream = TestJsonBodyWriter.class
                .getResourceAsStream("/invoke-rpc/json/rpc-output.json");
        final NormalizedNodeContext returnValue = this.jsonBodyReader.readFrom(null,
                null, null, this.mediaType, null, inputStream);
        final OutputStream output = new ByteArrayOutputStream();
        this.jsonBodyWriter.writeTo(returnValue, null, null, null, this.mediaType, null,
                output);
        assertTrue(output.toString().contains("lf-test"));
    }
}
