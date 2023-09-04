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
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.core.MediaType;
import org.junit.Test;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.common.patch.PatchStatusEntity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

public class JsonPatchStatusBodyWriterTest {

    /**
     * Test if per-operation status is omitted if global error is present.
     */
    @Test
    public void testErrorOutput() throws IOException {
        final var error = new RestconfError(ErrorType.PROTOCOL, new ErrorTag("data-exists"), "Data already exists");
        final var statusEntity = new PatchStatusEntity("patch1", false, new ArrayList<>(List.of(error)));

        final var contextWithGlobalError = new PatchStatusContext("patch", new ArrayList<>(List.of(statusEntity)),
            false, new ArrayList<>(List.of(error)));
        final var contextWithoutGlobalError = new PatchStatusContext("patch", new ArrayList<>(List.of(statusEntity)),
            false, null);

        final var outputWithGlobalError = new ByteArrayOutputStream();
        final var outputWithoutGlobalError = new ByteArrayOutputStream();
        final var writer = new JsonPatchStatusBodyWriter();

        writer.writeTo(contextWithGlobalError, null, null, null, MediaType.APPLICATION_XML_TYPE, null,
            outputWithGlobalError);
        writer.writeTo(contextWithoutGlobalError, null, null, null, MediaType.APPLICATION_XML_TYPE, null,
            outputWithoutGlobalError);

        assertEquals("{\"ietf-yang-patch:yang-patch-status\":{"
            + "\"patch-id\":\"patch\","
            + "\"errors\":{\"error\":["
            + "{\"error-type\":\"protocol\","
            + "\"error-tag\":\"data-exists\","
            + "\"error-message\":\"Data already exists\""
            + "}]}}}", outputWithGlobalError.toString(StandardCharsets.UTF_8));

        assertEquals("{\"ietf-yang-patch:yang-patch-status\":{"
            + "\"patch-id\":\"patch\","
            + "\"edit-status\":{\"edit\":["
            + "{\"edit-id\":\"patch1\","
            + "\"errors\":{\"error\":["
            + "{\"error-type\":\"protocol\","
            + "\"error-tag\":\"data-exists\","
            + "\"error-message\":\"Data already exists\""
            + "}]}}]}}}", outputWithoutGlobalError.toString(StandardCharsets.UTF_8));
    }
}
