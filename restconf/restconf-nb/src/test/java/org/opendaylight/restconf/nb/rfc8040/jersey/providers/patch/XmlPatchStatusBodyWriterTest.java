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

public class XmlPatchStatusBodyWriterTest {

    /**
     * Test if per-operation status is omitted if global error is present.
     */
    @Test
    public void testErrorOutput() throws IOException {
        final var error = new RestconfError(ErrorType.PROTOCOL, new ErrorTag("data-exists"), "Data already exists");
        final var statusEntity = new PatchStatusEntity("patch1", true, null);
        final var statusEntityError = new PatchStatusEntity("patch1", false, List.of(error));

        final var contextWithGlobalError = new PatchStatusContext("patch", List.of(statusEntity),
            false, List.of(error));
        final var contextWithoutGlobalError = new PatchStatusContext("patch", List.of(statusEntityError),
            false, null);

        final var outputWithGlobalError = new ByteArrayOutputStream();
        final var outputWithoutGlobalError = new ByteArrayOutputStream();
        final var writer = new XmlPatchStatusBodyWriter();

        writer.writeTo(contextWithGlobalError, null, null, null, MediaType.APPLICATION_XML_TYPE, null,
            outputWithGlobalError);
        writer.writeTo(contextWithoutGlobalError, null, null, null, MediaType.APPLICATION_XML_TYPE, null,
            outputWithoutGlobalError);

        assertEquals("<yang-patch-status xmlns=\"urn:ietf:params:xml:ns:yang:ietf-yang-patch\">"
            + "<patch-id>patch</patch-id>"
            + "<errors>"
            + "<error-type>protocol</error-type>"
            + "<error-tag>data-exists</error-tag>"
            + "<error-message>Data already exists</error-message>"
            + "</errors>"
            + "</yang-patch-status>", outputWithGlobalError.toString(StandardCharsets.UTF_8));

        assertEquals("<yang-patch-status xmlns=\"urn:ietf:params:xml:ns:yang:ietf-yang-patch\">"
            + "<patch-id>patch</patch-id>"
            + "<edit-status><edit>"
            + "<edit-id>patch1</edit-id>"
            + "<errors>"
            + "<error-type>protocol</error-type>"
            + "<error-tag>data-exists</error-tag>"
            + "<error-message>Data already exists</error-message>"
            + "</errors></edit></edit-status>"
            + "</yang-patch-status>", outputWithoutGlobalError.toString(StandardCharsets.UTF_8));
    }
}
