/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.sal.rest.impl.test.providers;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import java.io.InputStream;
import javax.ws.rs.core.MediaType;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.netconf.sal.rest.impl.JsonToPATCHBodyReader;
import org.opendaylight.netconf.sal.restconf.impl.PATCHContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class TestJsonPATCHBodyReader extends AbstractBodyReaderTest {

    private final JsonToPATCHBodyReader jsonPATCHBodyReader;
    private static SchemaContext schemaContext;

    public TestJsonPATCHBodyReader() throws NoSuchFieldException, SecurityException {
        super();
        jsonPATCHBodyReader = new JsonToPATCHBodyReader();
    }

    @Override
    protected MediaType getMediaType() {
        return new MediaType(APPLICATION_JSON, null);
    }

    @BeforeClass
    public static void initialization() {
        schemaContext = schemaContextLoader("/instanceidentifier/yang", schemaContext);
        controllerContext.setSchemas(schemaContext);
    }

    @Test
    public void modulePATCHDataTest() throws Exception {
        final String uri = "instance-identifier-patch-module:patch-cont/my-list1/leaf1";
        mockBodyReader(uri, jsonPATCHBodyReader, false);

        final InputStream inputStream = TestJsonBodyReader.class
                .getResourceAsStream("/instanceidentifier/json/jsonPATCHdata.json");

        final PATCHContext returnValue = jsonPATCHBodyReader
                .readFrom(null, null, null, mediaType, null, inputStream);
        checkPATCHContext(returnValue);
    }
}
