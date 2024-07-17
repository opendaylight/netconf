/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.server.api.PatchStatusContext;
import org.opendaylight.restconf.server.api.PatchStatusEntity;
import org.opendaylight.restconf.server.api.ServerError;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

class YangPatchStatusBodyTest extends AbstractJukeboxTest {
    private final ServerError error = new ServerError(ErrorType.PROTOCOL, new ErrorTag("data-exists"),
        "Data already exists");
    private final PatchStatusEntity statusEntity = new PatchStatusEntity("patch1", true, null);
    private final PatchStatusEntity statusEntityError = new PatchStatusEntity("patch1", false, List.of(error));

    /**
     * Test if per-operation status is omitted if global error is present.
     */
    @Test
    void testOutputWithGlobalError() {
        final var body = new YangPatchStatusBody(new PatchStatusContext("patch", List.of(statusEntity), false,
            List.of(error)));

        assertFormat("""
            {
              "ietf-yang-patch:yang-patch-status": {
                "patch-id": "patch",
                "errors": {
                  "error": [
                    {
                      "error-type": "protocol",
                      "error-tag": "data-exists",
                      "error-message": "Data already exists"
                    }
                  ]
                }
              }
            }""", body::formatToJSON, true);
        assertFormat("""
            <yang-patch-status xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
              <patch-id>patch</patch-id>
              <errors>
                <error-type>protocol</error-type>
                <error-tag>data-exists</error-tag>
                <error-message>Data already exists</error-message>
              </errors>
            </yang-patch-status>""", body::formatToXML, true);
    }

    /**
     * Test if per-operation status is present if there is no global error present.
     */
    @Test
    void testOutputWithoutGlobalError() {
        final var body = new YangPatchStatusBody(new PatchStatusContext("patch", List.of(statusEntityError), false,
            null));

        assertFormat("""
            {
              "ietf-yang-patch:yang-patch-status": {
                "patch-id": "patch",
                "edit-status": {
                  "edit": [
                    {
                      "edit-id": "patch1",
                      "errors": {
                        "error": [
                          {
                            "error-type": "protocol",
                            "error-tag": "data-exists",
                            "error-message": "Data already exists"
                          }
                        ]
                      }
                    }
                  ]
                }
              }
            }""", body::formatToJSON, true);
        assertFormat("""
            <yang-patch-status xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
              <patch-id>patch</patch-id>
              <edit-status>
                <edit>
                  <edit-id>patch1</edit-id>
                  <errors>
                    <error-type>protocol</error-type>
                    <error-tag>data-exists</error-tag>
                    <error-message>Data already exists</error-message>
                  </errors>
                </edit>
              </edit-status>
            </yang-patch-status>""", body::formatToXML, true);
    }
}
