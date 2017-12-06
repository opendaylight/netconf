/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.jersey.providers;

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
import org.opendaylight.controller.sal.rest.impl.test.providers.TestJsonBodyReader;
import org.opendaylight.netconf.sal.restconf.impl.PatchContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.restconf.RestConnectorProvider;
import org.opendaylight.restconf.handlers.SchemaContextHandler;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class JsonPatchBodyReaderMountPointTest extends AbstractBodyReaderTest {

    private static final String MOUNT_POINT = "instance-identifier-module:cont/yang-ext:mount/";
    private static SchemaContext schemaContext;
    private final JsonToPatchBodyReader jsonToPatchBodyReader;

    public JsonPatchBodyReaderMountPointTest() throws Exception {
        super();
        jsonToPatchBodyReader = new JsonToPatchBodyReader();
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

        when(MOUNT_POINT_SERVICE_HANDLER.get()).thenReturn(mountPointService);
        when(mountPointService.getMountPoint(any(YangInstanceIdentifier.class))).thenReturn(Optional.of(mountPoint));
        when(mountPoint.getSchemaContext()).thenReturn(schemaContext);

        final SchemaContextHandler schemaContextHandler = mock(SchemaContextHandler.class);
        when(schemaContextHandler.get()).thenReturn(schemaContext);
        RestConnectorProvider.setSchemaContextHandler(schemaContextHandler);
    }

    @Test
    public void modulePatchDataTest() throws Exception {
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
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
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
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
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        final InputStream inputStream = TestJsonBodyReader.class
                .getResourceAsStream("/instanceidentifier/json/jsonPATCHdataValueMissing.json");

        try {
            jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
            fail("Test should return error 400 due to missing value node when attempt to invoke create operation");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error code 400 expected", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Test trying to use value with Patch delete operation which does not support value. Test should fail with
     * {@link RestconfDocumentedException} with error code 400.
     */
    @Test
    public void modulePatchValueNotSupportedNegativeTest() throws Exception {
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        final InputStream inputStream = TestJsonBodyReader.class
                .getResourceAsStream("/instanceidentifier/json/jsonPATCHdataValueNotSupported.json");

        try {
            jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
            fail("Test should return error 400 due to present value node when attempt to invoke delete operation");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Error code 400 expected", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Test using Patch when target is completely specified in request URI and thus target leaf contains only '/' sign.
     */
    @Test
    public void modulePatchCompleteTargetInURITest() throws Exception {
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont";
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
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
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
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont";
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
        final String uri = MOUNT_POINT + "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        final InputStream inputStream = TestJsonBodyReader.class
                .getResourceAsStream("/instanceidentifier/json/jsonPATCHSimpleLeafValue.json");

        final PatchContext returnValue = jsonToPatchBodyReader
                .readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContext(returnValue);
    }
}
