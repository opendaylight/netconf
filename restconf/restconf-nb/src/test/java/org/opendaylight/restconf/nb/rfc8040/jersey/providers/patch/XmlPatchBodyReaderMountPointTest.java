/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.ws.rs.core.MediaType;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.test.AbstractBodyReaderTest;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.test.XmlBodyReaderTest;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public class XmlPatchBodyReaderMountPointTest extends AbstractBodyReaderTest {
    private static final String MOUNT_POINT = "instance-identifier-module:cont/yang-ext:mount/";

    private static EffectiveModelContext schemaContext;

    private final XmlPatchBodyReader xmlToPatchBodyReader;

    public XmlPatchBodyReaderMountPointTest() throws Exception {
        super(schemaContext);
        xmlToPatchBodyReader = new XmlPatchBodyReader(databindProvider, mountPointService);
    }

    @Override
    protected MediaType getMediaType() {
        return new MediaType(MediaType.APPLICATION_XML, null);
    }

    @BeforeClass
    public static void initialization() {
        schemaContext = schemaContextLoader("/instanceidentifier/yang", schemaContext);
    }

    @Test
    public void moduleDataTest() throws Exception {
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, xmlToPatchBodyReader, false);

        final InputStream inputStream = XmlBodyReaderTest.class.getResourceAsStream(
            "/instanceidentifier/xml/xmlPATCHdata.xml");
        final PatchContext returnValue = xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContextMountPoint(returnValue);
    }

    /**
     * Test trying to use Patch create operation which requires value without value. Error code 400 should be returned.
     */
    @Test
    public void moduleDataValueMissingNegativeTest() throws Exception {
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, xmlToPatchBodyReader, false);
        final InputStream inputStream = XmlBodyReaderTest.class.getResourceAsStream(
            "/instanceidentifier/xml/xmlPATCHdataValueMissing.xml");

        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream));
        assertEquals(ErrorTag.MALFORMED_MESSAGE, ex.getErrors().get(0).getErrorTag());
    }

    /**
     * Test trying to use value with Patch delete operation which does not support value. Error code 400 should be
     * returned.
     */
    @Test
    public void moduleDataNotValueNotSupportedNegativeTest() throws Exception {
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, xmlToPatchBodyReader, false);
        final InputStream inputStream = XmlBodyReaderTest.class.getResourceAsStream(
            "/instanceidentifier/xml/xmlPATCHdataValueNotSupported.xml");

        RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream));
        assertEquals(ErrorTag.MALFORMED_MESSAGE, ex.getErrors().get(0).getErrorTag());
    }

    /**
     * Test of Yang Patch with absolute target path.
     */
    @Test
    public void moduleDataAbsoluteTargetPathTest() throws Exception {
        final String uri = MOUNT_POINT;
        mockBodyReader(uri, xmlToPatchBodyReader, false);

        checkPatchContextMountPoint(xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            XmlBodyReaderTest.class.getResourceAsStream(
                "/instanceidentifier/xml/xmlPATCHdataAbsoluteTargetPath.xml")));
    }

    /**
     * Test using Patch when target is completely specified in request URI and thus target leaf contains only '/' sign.
     */
    @Test
    public void modulePatchCompleteTargetInURITest() throws Exception {
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont";
        mockBodyReader(uri, xmlToPatchBodyReader, false);

        checkPatchContextMountPoint(xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            XmlBodyReaderTest.class.getResourceAsStream(
                "/instanceidentifier/xml/xmlPATCHdataCompleteTargetInURI.xml")));
    }

    /**
     * Test of Yang Patch merge operation on list. Test consists of two edit operations - replace and merge.
     */
    @Test
    public void moduleDataMergeOperationOnListTest() throws Exception {
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, xmlToPatchBodyReader, false);

        checkPatchContextMountPoint(xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            XmlBodyReaderTest.class.getResourceAsStream(
                "/instanceidentifier/xml/xmlPATCHdataMergeOperationOnList.xml")));
    }

    /**
     * Test of Yang Patch merge operation on container. Test consists of two edit operations - create and merge.
     */
    @Test
    public void moduleDataMergeOperationOnContainerTest() throws Exception {
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont";
        mockBodyReader(uri, xmlToPatchBodyReader, false);

        checkPatchContextMountPoint(xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            XmlBodyReaderTest.class.getResourceAsStream(
                "/instanceidentifier/xml/xmlPATCHdataMergeOperationOnContainer.xml")));
    }

    /**
     * Test reading simple leaf value.
     */
    @Test
    public void modulePatchSimpleLeafValueTest() throws Exception {
        mockBodyReader("instance-identifier-patch-module:patch-cont/my-list1=leaf1", xmlToPatchBodyReader, false);
        final var inputStream = new ByteArrayInputStream("""
                <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                    <patch-id>test-patch</patch-id>
                    <comment>this is test patch</comment>
                    <edit>
                        <edit-id>edit1</edit-id>
                        <operation>replace</operation>
                        <target>/my-list2=my-leaf20/name</target>
                        <value>
                            <name xmlns="instance:identifier:patch:module">my-leaf20</name>
                        </value>
                    </edit>
                </yang-patch>
                """.getBytes(StandardCharsets.UTF_8));
        final var returnValue = xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContext(returnValue);
        final var expected = Builders.leafBuilder()
                .withValue("my-leaf20")
                .withNodeIdentifier(NodeIdentifier.create(NAME_QNAME))
                .build();
        final var data = returnValue.getData().get(0).getNode();
        assertEquals(NAME_QNAME, data.getIdentifier().getNodeType());
        assertEquals(expected, data);
    }
}
