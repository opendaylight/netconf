/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.InputStream;
import javax.ws.rs.core.MediaType;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.test.AbstractBodyReaderTest;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.test.JsonBodyReaderTest;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public class JsonPatchBodyReaderMountPointTest extends AbstractBodyReaderTest {
    private static final String MOUNT_POINT = "instance-identifier-module:cont/yang-ext:mount/";
    private static EffectiveModelContext schemaContext;

    private final JsonPatchBodyReader jsonToPatchBodyReader;

    public JsonPatchBodyReaderMountPointTest() throws Exception {
        super(schemaContext);
        jsonToPatchBodyReader = new JsonPatchBodyReader(schemaContextHandler, mountPointService);
    }

    @Override
    protected MediaType getMediaType() {
        return new MediaType(APPLICATION_JSON, null);
    }

    @BeforeClass
    public static void initialization() {
        schemaContext = schemaContextLoader("/instanceidentifier/yang", schemaContext);
    }

    @Test
    public void modulePatchDataTest() throws Exception {
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        checkPatchContextMountPoint(jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            JsonBodyReaderTest.class.getResourceAsStream("/instanceidentifier/json/jsonPATCHdata.json")));
    }

    /**
     * Test of successful Patch consisting of create and delete Patch operations.
     */
    @Test
    public void modulePatchCreateAndDeleteTest() throws Exception {
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        checkPatchContextMountPoint(jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            JsonBodyReaderTest.class.getResourceAsStream(
                "/instanceidentifier/json/jsonPATCHdataCreateAndDelete.json")));
    }

    /**
     * Test trying to use Patch create operation which requires value without value. Test should fail with
     * {@link RestconfDocumentedException} with error code 400.
     */
    @Test
    public void modulePatchValueMissingNegativeTest() throws Exception {
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        final InputStream inputStream = JsonBodyReaderTest.class.getResourceAsStream(
            "/instanceidentifier/json/jsonPATCHdataValueMissing.json");

        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream));
        assertEquals(ErrorTag.MALFORMED_MESSAGE, ex.getErrors().get(0).getErrorTag());
    }

    /**
     * Test trying to use value with Patch delete operation which does not support value. Test should fail with
     * {@link RestconfDocumentedException} with error code 400.
     */
    @Test
    public void modulePatchValueNotSupportedNegativeTest() throws Exception {
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        final InputStream inputStream = JsonBodyReaderTest.class.getResourceAsStream(
            "/instanceidentifier/json/jsonPATCHdataValueNotSupported.json");

        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream));
        assertEquals(ErrorTag.MALFORMED_MESSAGE, ex.getErrors().get(0).getErrorTag());
    }

    /**
     * Test using Patch when target is completely specified in request URI and thus target leaf contains only '/' sign.
     */
    @Test
    public void modulePatchCompleteTargetInURITest() throws Exception {
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        checkPatchContextMountPoint(jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            JsonBodyReaderTest.class.getResourceAsStream(
                "/instanceidentifier/json/jsonPATCHdataCompleteTargetInURI.json")));
    }

    /**
     * Test of Yang Patch merge operation on list. Test consists of two edit operations - replace and merge.
     */
    @Test
    public void modulePatchMergeOperationOnListTest() throws Exception {
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        checkPatchContextMountPoint(jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            JsonBodyReaderTest.class.getResourceAsStream(
                "/instanceidentifier/json/jsonPATCHMergeOperationOnList.json")));
    }

    /**
     * Test of Yang Patch merge operation on container. Test consists of two edit operations - create and merge.
     */
    @Test
    public void modulePatchMergeOperationOnContainerTest() throws Exception {
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        checkPatchContextMountPoint(jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            JsonBodyReaderTest.class.getResourceAsStream(
                "/instanceidentifier/json/jsonPATCHMergeOperationOnContainer.json")));
    }

    /**
     * Test reading simple leaf value.
     */
    @Test
    public void modulePatchSimpleLeafValueTest() throws Exception {
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        checkPatchContext(jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            JsonBodyReaderTest.class.getResourceAsStream("/instanceidentifier/json/jsonPATCHSimpleLeafValue.json")));
    }
}
