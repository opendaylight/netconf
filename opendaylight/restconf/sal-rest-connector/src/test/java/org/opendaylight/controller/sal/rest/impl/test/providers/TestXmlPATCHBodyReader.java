/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.sal.rest.impl.test.providers;

import static org.junit.Assert.assertNotNull;

import java.io.InputStream;
import javax.ws.rs.core.MediaType;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.netconf.sal.rest.impl.XmlToPATCHBodyReader;
import org.opendaylight.netconf.sal.restconf.impl.PATCHContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class TestXmlPATCHBodyReader extends AbstractBodyReaderTest {

    private final XmlToPATCHBodyReader xmlPATCHBodyReader;
    private static SchemaContext schemaContext;

    public TestXmlPATCHBodyReader() throws NoSuchFieldException, SecurityException {
        super();
        xmlPATCHBodyReader = new XmlToPATCHBodyReader();
    }

    @Override
    protected MediaType getMediaType() {
        return new MediaType(MediaType.APPLICATION_XML, null);
    }

    @BeforeClass
    public static void initialization() throws NoSuchFieldException, SecurityException {
        schemaContext = schemaContextLoader("/instanceidentifier/yang", schemaContext);
        controllerContext.setSchemas(schemaContext);
    }

    @Test
    public void moduleDataTest() throws Exception {
        final String uri = "instance-identifier-patch-module:patch-cont/my-list1/leaf1";
        mockBodyReader(uri, xmlPATCHBodyReader, false);
        final InputStream inputStream = TestXmlBodyReader.class
                .getResourceAsStream("/instanceidentifier/xml/xmlPATCHdata.xml");
        final PATCHContext returnValue = xmlPATCHBodyReader
                .readFrom(null, null, null, mediaType, null, inputStream);
        assertNotNull(returnValue);
    }
}
