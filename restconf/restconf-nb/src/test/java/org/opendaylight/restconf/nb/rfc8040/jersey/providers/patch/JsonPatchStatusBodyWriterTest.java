/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.ws.rs.core.MediaType;
import org.junit.Test;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.common.patch.PatchStatusEntity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

public class JsonPatchStatusBodyWriterTest {
    private final RestconfError error = new RestconfError(ErrorType.PROTOCOL, new ErrorTag("data-exists"),
        "Data already exists");
    private final PatchStatusEntity statusEntity = new PatchStatusEntity("patch1", true, null);
    private final PatchStatusEntity statusEntityError = new PatchStatusEntity("patch1", false, List.of(error));
    private final JsonPatchStatusBodyWriter writer = new JsonPatchStatusBodyWriter();

    /**
     * Test if per-operation status is omitted if global error is present.
     */
    @Test
    public void testOutputWithGlobalError() throws IOException {
        final var outputStream = new ByteArrayOutputStream();
        final var patchStatusContext = new PatchStatusContext("patch", List.of(statusEntity),
            false, List.of(error));
        writer.writeTo(patchStatusContext, null, null, null, MediaType.APPLICATION_XML_TYPE, null, outputStream);

        assertEquals("""
            {"ietf-yang-patch:yang-patch-status":{\
            "patch-id":"patch",\
            "errors":{"error":[{\
            "error-type":"protocol",\
            "error-tag":"data-exists",\
            "error-message":"Data already exists"\
            }]}}}""", outputStream.toString(StandardCharsets.UTF_8));
    }

    /**
     * Test if per-operation status is present if there is no global error present.
     */
    @Test
    public void testOutputWithoutGlobalError() throws IOException {
        final var outputStream = new ByteArrayOutputStream();
        final var patchStatusContext = new PatchStatusContext("patch", List.of(statusEntityError),
            false, null);
        writer.writeTo(patchStatusContext, null, null, null, MediaType.APPLICATION_XML_TYPE, null, outputStream);

        assertEquals("""
            {"ietf-yang-patch:yang-patch-status":{\
            "patch-id":"patch",\
            "edit-status":{"edit":[{\
            "edit-id":"patch1",\
            "errors":{"error":[{\
            "error-type":"protocol",\
            "error-tag":"data-exists",\
            "error-message":"Data already exists"\
            }]}}]}}}""", outputStream.toString(StandardCharsets.UTF_8));
    }
}
