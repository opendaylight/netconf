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
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import java.io.InputStream;
import javax.ws.rs.core.MediaType;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.sal.restconf.impl.PATCHContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.restconf.utils.patch.Draft16JsonToPATCHBodyReader;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class TestDraft16JsonPATCHBodyReaderMountPoint extends Draft16AbstractBodyReaderTest {

    private final Draft16JsonToPATCHBodyReader jsonPATCHBodyReader;
    private static SchemaContext schemaContext;
    private static final String MOUNT_POINT = "instance-identifier-module:cont/yang-ext:mount/";

    public TestDraft16JsonPATCHBodyReaderMountPoint() throws Exception {
        super();
        jsonPATCHBodyReader = new Draft16JsonToPATCHBodyReader();
    }

    @Override
    protected MediaType getMediaType() {
        return new MediaType(APPLICATION_JSON, null);
    }

    @BeforeClass
    public static void initialization() {
        schemaContext = schemaContextLoader("/instanceidentifier/yang", schemaContext);

        final DOMMountPointService mountPointService = mock(DOMMountPointService.class);
        final DOMMountPoint mountPoint = mock(DOMMountPoint.class);

        when(mountPointServiceHandler.get()).thenReturn(mountPointService);
        when(mountPointService.getMountPoint(any(YangInstanceIdentifier.class))).thenReturn(Optional.of(mountPoint));
        when(mountPoint.getSchemaContext()).thenReturn(schemaContext);

        controllerContext.setSchemas(schemaContext);
    }

    @Test
    public void modulePATCHDataTest() throws Exception {
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, jsonPATCHBodyReader, false);

        final InputStream inputStream = TestJsonBodyReader.class
                .getResourceAsStream("/instanceidentifier/json/jsonPATCHdata.json");

        final PATCHContext returnValue = jsonPATCHBodyReader
                .readFrom(null, null, null, mediaType, null, inputStream);
        checkPATCHContextMountPoint(returnValue);
    }

    /**
     * Test of successful PATCH consisting of create and delete PATCH operations.
     */
    @Test
    public void modulePATCHCreateAndDeleteTest() throws Exception {
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, jsonPATCHBodyReader, false);

        final InputStream inputStream = TestJsonBodyReader.class
                .getResourceAsStream("/instanceidentifier/json/jsonPATCHdataCreateAndDelete.json");

        final PATCHContext returnValue = jsonPATCHBodyReader
                .readFrom(null, null, null, mediaType, null, inputStream);
        checkPATCHContextMountPoint(returnValue);
    }

    /**
     * Test trying to use PATCH create operation which requires value without value. Test should fail with
     * {@link RestconfDocumentedException} with error code 400.
     */
    @Test
    public void modulePATCHValueMissingNegativeTest() throws Exception {
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, jsonPATCHBodyReader, false);

        final InputStream inputStream = TestJsonBodyReader.class
                .getResourceAsStream("/instanceidentifier/json/jsonPATCHdataValueMissing.json");

        try {
            jsonPATCHBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
            fail("Test should return error 400 due to missing value node when attempt to invoke create operation");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error code 400 expected", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Test trying to use value with PATCH delete operation which does not support value. Test should fail with
     * {@link RestconfDocumentedException} with error code 400.
     */
    @Test
    public void modulePATCHValueNotSupportedNegativeTest() throws Exception {
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, jsonPATCHBodyReader, false);

        final InputStream inputStream = TestJsonBodyReader.class
                .getResourceAsStream("/instanceidentifier/json/jsonPATCHdataValueNotSupported.json");

        try {
            jsonPATCHBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
            fail("Test should return error 400 due to present value node when attempt to invoke delete operation");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error code 400 expected", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Test using PATCH when target is completely specified in request URI and thus target leaf contains only '/' sign.
     */
    @Test
    public void modulePATCHCompleteTargetInURITest() throws Exception {
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont";
        mockBodyReader(uri, jsonPATCHBodyReader, false);

        final InputStream inputStream = TestJsonBodyReader.class
                .getResourceAsStream("/instanceidentifier/json/jsonPATCHdataCompleteTargetInURI.json");

        final PATCHContext returnValue = jsonPATCHBodyReader
                .readFrom(null, null, null, mediaType, null, inputStream);
        checkPATCHContextMountPoint(returnValue);
    }

    /**
     * Test of Yang PATCH merge operation on list. Test consists of two edit operations - replace and merge.
     */
    @Test
    public void modulePATCHMergeOperationOnListTest() throws Exception {
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, jsonPATCHBodyReader, false);

        final InputStream inputStream = TestJsonBodyReader.class
                .getResourceAsStream("/instanceidentifier/json/jsonPATCHMergeOperationOnList.json");

        final PATCHContext returnValue = jsonPATCHBodyReader
                .readFrom(null, null, null, mediaType, null, inputStream);
        checkPATCHContextMountPoint(returnValue);
    }

    /**
     * Test of Yang PATCH merge operation on container. Test consists of two edit operations - create and merge.
     */
    @Test
    public void modulePATCHMergeOperationOnContainerTest() throws Exception {
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont";
        mockBodyReader(uri, jsonPATCHBodyReader, false);

        final InputStream inputStream = TestJsonBodyReader.class
                .getResourceAsStream("/instanceidentifier/json/jsonPATCHMergeOperationOnContainer.json");

        final PATCHContext returnValue = jsonPATCHBodyReader
                .readFrom(null, null, null, mediaType, null, inputStream);
        checkPATCHContextMountPoint(returnValue);
    }
}
