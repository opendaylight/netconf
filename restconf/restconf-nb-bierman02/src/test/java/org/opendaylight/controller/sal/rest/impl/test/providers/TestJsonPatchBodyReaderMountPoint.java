/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.rest.impl.test.providers;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import java.io.InputStream;
import javax.ws.rs.core.MediaType;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.netconf.sal.rest.impl.JsonToPatchBodyReader;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public class TestJsonPatchBodyReaderMountPoint extends AbstractBodyReaderTest {
    private final JsonToPatchBodyReader jsonToPatchBodyReader;
    private static EffectiveModelContext schemaContext;
    private static final String MOUNT_POINT = "instance-identifier-module:cont/yang-ext:mount";

    public TestJsonPatchBodyReaderMountPoint() {
        super(schemaContext, mock(DOMMountPoint.class));
        jsonToPatchBodyReader = new JsonToPatchBodyReader(controllerContext);
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
        final String uri = MOUNT_POINT + "/instance-identifier-patch-module:patch-cont/my-list1/leaf1";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        final InputStream inputStream = TestJsonBodyReader.class
                .getResourceAsStream("/instanceidentifier/json/jsonPATCHdata.json");

        final PatchContext returnValue = jsonToPatchBodyReader
                .readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContextMountPoint(returnValue);
    }

    /**
     * Test of successful Patch consisting of create and delete Patch operations.
     */
    @Test
    public void modulePatchCreateAndDeleteTest() throws Exception {
        final String uri = MOUNT_POINT + "/instance-identifier-patch-module:patch-cont/my-list1/leaf1";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        final InputStream inputStream = TestJsonBodyReader.class
                .getResourceAsStream("/instanceidentifier/json/jsonPATCHdataCreateAndDelete.json");

        final PatchContext returnValue = jsonToPatchBodyReader
                .readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContextMountPoint(returnValue);
    }

    /**
     * Test trying to use Patch create operation which requires value without value. Test should fail with
     * {@link RestconfDocumentedException} with error code 400.
     */
    @Test
    public void modulePatchValueMissingNegativeTest() throws Exception {
        final String uri = MOUNT_POINT + "/instance-identifier-patch-module:patch-cont/my-list1/leaf1";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        final InputStream inputStream = TestJsonBodyReader.class.getResourceAsStream(
            "/instanceidentifier/json/jsonPATCHdataValueMissing.json");

        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream));
        assertEquals(ErrorTag.MALFORMED_MESSAGE, ex.getErrors().get(0).tag());
    }

    /**
     * Test trying to use value with Patch delete operation which does not support value. Test should fail with
     * {@link RestconfDocumentedException} with error code 400.
     */
    @Test
    public void modulePatchValueNotSupportedNegativeTest() throws Exception {
        final String uri = MOUNT_POINT + "/instance-identifier-patch-module:patch-cont/my-list1/leaf1";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        final InputStream inputStream = TestJsonBodyReader.class
                .getResourceAsStream("/instanceidentifier/json/jsonPATCHdataValueNotSupported.json");

        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream));
        assertEquals(ErrorTag.MALFORMED_MESSAGE, ex.getErrors().get(0).tag());
    }

    /**
     * Test using Patch when target is completely specified in request URI and thus target leaf contains only '/' sign.
     */
    @Test
    public void modulePatchCompleteTargetInURITest() throws Exception {
        final String uri = MOUNT_POINT + "/instance-identifier-patch-module:patch-cont";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        final InputStream inputStream = TestJsonBodyReader.class
                .getResourceAsStream("/instanceidentifier/json/jsonPATCHdataCompleteTargetInURI.json");

        final PatchContext returnValue = jsonToPatchBodyReader
                .readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContextMountPoint(returnValue);
    }

    /**
     * Test of Yang Patch merge operation on list. Test consists of two edit operations - replace and merge.
     */
    @Test
    public void modulePatchMergeOperationOnListTest() throws Exception {
        final String uri = MOUNT_POINT + "/instance-identifier-patch-module:patch-cont/my-list1/leaf1";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        final InputStream inputStream = TestJsonBodyReader.class
                .getResourceAsStream("/instanceidentifier/json/jsonPATCHMergeOperationOnList.json");

        final PatchContext returnValue = jsonToPatchBodyReader
                .readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContextMountPoint(returnValue);
    }

    /**
     * Test of Yang Patch merge operation on container. Test consists of two edit operations - create and merge.
     */
    @Test
    public void modulePatchMergeOperationOnContainerTest() throws Exception {
        final String uri = MOUNT_POINT + "/instance-identifier-patch-module:patch-cont";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        final InputStream inputStream = TestJsonBodyReader.class
                .getResourceAsStream("/instanceidentifier/json/jsonPATCHMergeOperationOnContainer.json");

        final PatchContext returnValue = jsonToPatchBodyReader
                .readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContextMountPoint(returnValue);
    }

    /**
     * Test reading simple leaf value.
     */
    @Test
    public void modulePatchSimpleLeafValueTest() throws Exception {
        final String uri = MOUNT_POINT + "/instance-identifier-patch-module:patch-cont/my-list1/leaf1";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        final InputStream inputStream = TestJsonBodyReader.class
                .getResourceAsStream("/instanceidentifier/json/jsonPATCHSimpleLeafValue.json");

        final PatchContext returnValue = jsonToPatchBodyReader
                .readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContext(returnValue);
    }
}
