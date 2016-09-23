/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.sal.rest.impl.test.providers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.InputStream;
import javax.ws.rs.core.MediaType;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.netconf.sal.restconf.impl.PATCHContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.restconf.utils.patch.Draft16XmlToPATCHBodyReader;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class TestDraft16XmlPATCHBodyReader extends Draft16AbstractBodyReaderTest {

    private final Draft16XmlToPATCHBodyReader xmlPATCHBodyReader;
    private static SchemaContext schemaContext;

    public TestDraft16XmlPATCHBodyReader() throws Exception {
        super();
        xmlPATCHBodyReader = new Draft16XmlToPATCHBodyReader();
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
        final String uri = "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, xmlPATCHBodyReader, false);
        final InputStream inputStream = TestXmlBodyReader.class
                .getResourceAsStream("/instanceidentifier/xml/xmlPATCHdata.xml");
        final PATCHContext returnValue = xmlPATCHBodyReader
                .readFrom(null, null, null, mediaType, null, inputStream);
        checkPATCHContext(returnValue);
    }

    /**
     * Test trying to use PATCH create operation which requires value without value. Error code 400 should be returned.
     */
    @Test
    public void moduleDataValueMissingNegativeTest() throws Exception {
        final String uri = "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, xmlPATCHBodyReader, false);
        final InputStream inputStream = TestXmlBodyReader.class
                .getResourceAsStream("/instanceidentifier/xml/xmlPATCHdataValueMissing.xml");
        try {
            xmlPATCHBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
            fail("Test should return error 400 due to missing value node when attempt to invoke create operation");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error code 400 expected", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Test trying to use value with PATCH delete operation which does not support value. Error code 400 should be
     * returned.
     */
    @Test
    public void moduleDataNotValueNotSupportedNegativeTest() throws Exception {
        final String uri = "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, xmlPATCHBodyReader, false);
        final InputStream inputStream = TestXmlBodyReader.class
                .getResourceAsStream("/instanceidentifier/xml/xmlPATCHdataValueNotSupported.xml");
        try {
            xmlPATCHBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
            fail("Test should return error 400 due to present value node when attempt to invoke delete operation");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error code 400 expected", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }


    /**
     * Test of Yang PATCH with absolute target path.
     */
    @Test
    public void moduleDataAbsoluteTargetPathTest() throws Exception {
        final String uri = "";
        mockBodyReader(uri, xmlPATCHBodyReader, false);
        final InputStream inputStream = TestXmlBodyReader.class
                .getResourceAsStream("/instanceidentifier/xml/xmlPATCHdataAbsoluteTargetPath.xml");
        final PATCHContext returnValue = xmlPATCHBodyReader
                .readFrom(null, null, null, mediaType, null, inputStream);
        checkPATCHContext(returnValue);
    }

    /**
     * Test using PATCH when target is completely specified in request URI and thus target leaf contains only '/' sign.
     */
    @Test
    public void modulePATCHCompleteTargetInURITest() throws Exception {
        final String uri = "instance-identifier-patch-module:patch-cont";
        mockBodyReader(uri, xmlPATCHBodyReader, false);
        final InputStream inputStream = TestXmlBodyReader.class
                .getResourceAsStream("/instanceidentifier/xml/xmlPATCHdataCompleteTargetInURI.xml");
        final PATCHContext returnValue = xmlPATCHBodyReader
                .readFrom(null, null, null, mediaType, null, inputStream);
        checkPATCHContext(returnValue);
    }

    /**
     * Test of Yang PATCH merge operation on list. Test consists of two edit operations - replace and merge.
     */
    @Test
    public void moduleDataMergeOperationOnListTest() throws Exception {
        final String uri = "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, xmlPATCHBodyReader, false);
        final InputStream inputStream = TestXmlBodyReader.class
                .getResourceAsStream("/instanceidentifier/xml/xmlPATCHdataMergeOperationOnList.xml");
        final PATCHContext returnValue = xmlPATCHBodyReader
                .readFrom(null, null, null, mediaType, null, inputStream);
        checkPATCHContext(returnValue);
    }

    /**
     * Test of Yang PATCH merge operation on container. Test consists of two edit operations - create and merge.
     */
    @Test
    public void moduleDataMergeOperationOnContainerTest() throws Exception {
        final String uri = "instance-identifier-patch-module:patch-cont";
        mockBodyReader(uri, xmlPATCHBodyReader, false);
        final InputStream inputStream = TestXmlBodyReader.class
                .getResourceAsStream("/instanceidentifier/xml/xmlPATCHdataMergeOperationOnContainer.xml");
        final PATCHContext returnValue = xmlPATCHBodyReader
                .readFrom(null, null, null, mediaType, null, inputStream);
        checkPATCHContext(returnValue);
    }
}
