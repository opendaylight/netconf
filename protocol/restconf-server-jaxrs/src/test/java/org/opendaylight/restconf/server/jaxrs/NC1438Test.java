/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.doReturn;

import java.util.Map;
import java.util.function.Consumer;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.YangErrorsBody;

@ExtendWith(MockitoExtension.class)
public class NC1438Test extends AbstractRestconfTest {

    @BeforeEach
    void beforeEach() {
        doReturn(new MultivaluedHashMap<>(Map.of(PrettyPrintParam.uriName, "true"))).when(uriInfo).getQueryParameters();
    }

    @Test
    void testPatchWithMultipleValues() {
        final var body = assert400PatchError(ar -> restconf.dataYangJsonPATCH(stringInputStream("""
            {
              "ietf-yang-patch:yang-patch" : {
                "patch-id" : "test patch id",
                "edit" : [
                  {
                    "edit-id" : "create data",
                    "operation" : "create",
                    "target" : "/example-jukebox:jukebox",
                    "value" : {
                      "jukebox" : {
                        "player" : {
                          "gap" : "0.2"
                        }
                      }
                    },
                    "value" : {
                      "jukebox" : {
                        "player" : {
                          "gap" : "0.2"
                        }
                      }
                    }
                  }
                ]
              }
            }"""), uriInfo, sc, ar));

        assertFormat("""
            {
              "errors": {
                "error": [
                  {
                    "error-tag": "malformed-message",
                    "error-message": "Multiple value entries found",
                    "error-type": "application"
                  }
                ]
              }
            }""", body::formatToJSON, true);
    }

    @Test
    void testPatchWithMultipleValuesAndDeferredUsed() {
        final var body = assert400PatchError(ar -> restconf.dataYangJsonPATCH(stringInputStream("""
            {
              "ietf-yang-patch:yang-patch" : {
                "patch-id" : "test patch id",
                "edit" : [
                  {
                    "edit-id" : "create data",
                    "operation" : "create",
                    "value" : {
                      "jukebox" : {
                        "player" : {
                          "gap" : "0.2"
                        }
                      }
                    },
                    "target" : "/example-jukebox:jukebox",
                    "value" : {
                      "jukebox" : {
                        "player" : {
                          "gap" : "0.2"
                        }
                      }
                    }
                  }
                ]
              }
            }"""), uriInfo, sc, ar));

        assertFormat("""
            {
              "errors": {
                "error": [
                  {
                    "error-tag": "malformed-message",
                    "error-message": "Multiple value entries found",
                    "error-type": "application"
                  }
                ]
              }
            }""", body::formatToJSON, true);
    }

    @Test
    @SuppressWarnings("checkstyle:LineLength")
    void testPatchMissingEditIdData() {
        final var body = assert400PatchError(ar -> restconf.dataYangJsonPATCH(stringInputStream("""
            {
              "ietf-yang-patch:yang-patch" : {
                "patch-id" : "test patch id",
                "edit" : [
                  {
                    "operation" : "create",
                    "target" : "/example-jukebox:jukebox",
                    "value" : {
                      "jukebox" : {
                        "player" : {
                          "gap" : "0.2"
                        }
                      }
                    }
                  }
                ]
              }
            }"""), uriInfo, sc, ar));

        assertFormat("""
            {
              "errors": {
                "error": [
                  {
                    "error-tag": "malformed-message",
                    "error-message": "Missing required schema node (urn:ietf:params:xml:ns:yang:ietf-yang-patch?revision=2017-02-22)edit-id",
                    "error-type": "application"
                  }
                ]
              }
            }""", body::formatToJSON, true);
    }

    @Test
    @SuppressWarnings("checkstyle:LineLength")
    void testPatchMissingPatchIdData() {
        final var body = assert400PatchError(ar -> restconf.dataYangJsonPATCH(stringInputStream("""
            {
              "ietf-yang-patch:yang-patch" : {
                "edit" : [
                  {
                    "edit-id" : "create data",
                    "operation" : "create",
                    "target" : "/example-jukebox:jukebox",
                    "value" : {
                      "jukebox" : {
                        "player" : {
                          "gap" : "0.2"
                        }
                      }
                    }
                  }
                ]
              }
            }"""), uriInfo, sc, ar));

        assertFormat("""
            {
              "errors": {
                "error": [
                  {
                    "error-tag": "malformed-message",
                    "error-message": "Missing required schema node (urn:ietf:params:xml:ns:yang:ietf-yang-patch?revision=2017-02-22)patch-id",
                    "error-type": "application"
                  }
                ]
              }
            }""", body::formatToJSON, true);
    }

    @Test
    @SuppressWarnings("checkstyle:LineLength")
    void testPatchMissingTarget() {
        final var body = assert400PatchError(ar -> restconf.dataYangJsonPATCH(stringInputStream("""
            {
              "ietf-yang-patch:yang-patch" : {
                "edit" : [
                  {
                    "edit-id" : "create data",
                    "operation" : "create",
                    "value" : {
                      "jukebox" : {
                        "player" : {
                          "gap" : "0.2"
                        }
                      }
                    }
                  }
                ]
              }
            }"""), uriInfo, sc, ar));

        assertFormat("""
            {
              "errors": {
                "error": [
                  {
                    "error-tag": "malformed-message",
                    "error-message": "Missing required schema node (urn:ietf:params:xml:ns:yang:ietf-yang-patch?revision=2017-02-22)target",
                    "error-type": "application"
                  }
                ]
              }
            }""", body::formatToJSON, true);
    }

    @Test
    @SuppressWarnings("checkstyle:LineLength")
    void testPatchMissingOperation() {
        final var body = assert400PatchError(ar -> restconf.dataYangJsonPATCH(stringInputStream("""
            {
              "ietf-yang-patch:yang-patch" : {
                "edit" : [
                  {
                    "edit-id" : "create data",
                    "target" : "/example-jukebox:jukebox",
                    "value" : {
                      "jukebox" : {
                        "player" : {
                          "gap" : "0.2"
                        }
                      }
                    }
                  }
                ]
              }
            }"""), uriInfo, sc, ar));

        assertFormat("""
            {
              "errors": {
                "error": [
                  {
                    "error-tag": "malformed-message",
                    "error-message": "Missing required schema node (urn:ietf:params:xml:ns:yang:ietf-yang-patch?revision=2017-02-22)operation",
                    "error-type": "application"
                  }
                ]
              }
            }""", body::formatToJSON, true);
    }

    @Test
    @SuppressWarnings("checkstyle:LineLength")
    void testPatchWithEmptyValue() {
        final var body = assert400PatchError(ar -> restconf.dataYangJsonPATCH(stringInputStream("""
            {
              "ietf-yang-patch:yang-patch" : {
                "edit" : [
                  {
                    "edit-id" : "create data",
                    "operation" : "create",
                    "target" : "/example-jukebox:jukebox"
                  }
                ]
              }
            }"""), uriInfo, sc, ar));

        assertFormat("""
            {
              "errors": {
                "error": [
                  {
                    "error-tag": "malformed-message",
                    "error-message": "Provided 'operation' value Create requires a non-empty value for the 'value' field",
                    "error-type": "application"
                  }
                ]
              }
            }""", body::formatToJSON, true);
    }

    @Test
    void testPatchWithRequiredEmptyValue() {
        final var body = assert400PatchError(ar -> restconf.dataYangJsonPATCH(stringInputStream("""
            {
              "ietf-yang-patch:yang-patch" : {
                "edit" : [
                  {
                    "edit-id" : "create data",
                    "operation" : "delete",
                    "target" : "/example-jukebox:jukebox",
                    "value" : {
                      "jukebox" : {
                        "player" : {
                          "gap" : "0.2"
                        }
                      }
                    }
                  }
                ]
              }
            }"""), uriInfo, sc, ar));

        assertFormat("""
            {
              "errors": {
                "error": [
                  {
                    "error-tag": "malformed-message",
                    "error-message": "Provided 'operation' value Delete requires an empty value for the 'value' field",
                    "error-type": "application"
                  }
                ]
              }
            }""", body::formatToJSON, true);
    }

    @Test
    @SuppressWarnings("checkstyle:LineLength")
    void testIncorrectEditIdValue() {
        final var body = assert400PatchError(ar -> restconf.dataYangJsonPATCH(stringInputStream("""
            {
              "ietf-yang-patch:yang-patch" : {
                "edit" : [
                  {
                    "edit-id" : 1,
                    "operation" : "create",
                    "target" : "/example-jukebox:jukebox",
                    "value" : {
                      "jukebox" : {
                        "player" : {
                          "gap" : "0.2"
                        }
                      }
                    }
                  }
                ]
              }
            }"""), uriInfo, sc, ar));

        assertFormat("""
            {
              "errors": {
                "error": [
                  {
                    "error-tag": "malformed-message",
                    "error-message": "Expected STRING for value of 'edit-id', but received NUMBER",
                    "error-type": "application"
                  }
                ]
              }
            }""", body::formatToJSON, true);
    }

    @Test
    void testPatchWrongOperationData() {
        final var body = assert400PatchError(ar -> restconf.dataYangJsonPATCH(stringInputStream("""
            {
              "ietf-yang-patch:yang-patch" : {
                "patch-id" : "test patch id",
                "edit" : [
                  {
                    "edit-id" : "create data",
                    "operation" : "wrong",
                    "target" : "/example-jukebox:jukebox",
                    "value" : {
                      "jukebox" : {
                        "player" : {
                          "gap" : "0.2"
                        }
                      }
                    }
                  }
                ]
              }
            }"""), uriInfo, sc, ar));

        assertFormat("""
            {
              "errors": {
                "error": [
                  {
                    "error-tag": "malformed-message",
                    "error-message": "\\"wrong\\" is not a valid name",
                    "error-type": "application"
                  }
                ]
              }
            }""", body::formatToJSON, true);
    }

    @Test
    @SuppressWarnings("checkstyle:LineLength")
    void testPatchWrongLeafData() {
        final var body = assert400PatchError(ar -> restconf.dataYangJsonPATCH(stringInputStream("""
            {
              "ietf-yang-patch:yang-patch" : {
                "patch-id" : "test patch id",
                "edit" : [
                  {
                    "edit-id" : "create data",
                    "operation" : "create",
                    "target" : "/example-jukebox:jukebox",
                    "value" : {
                      "jukebox" : {
                        "player" : {
                          "wrong" : "0.2"
                        }
                      }
                    }
                  }
                ]
              }
            }"""), uriInfo, sc, ar));

        assertFormat("""
            {
              "errors": {
                "error": [
                  {
                    "error-tag": "malformed-message",
                    "error-message": "Schema node with name wrong was not found under (http://example.com/ns/example-jukebox?revision=2015-04-04)player.",
                    "error-type": "application"
                  }
                ]
              }
            }""", body::formatToJSON, true);
    }

    private static YangErrorsBody assert400PatchError(final Consumer<AsyncResponse> invocation) {
        return assertInstanceOf(YangErrorsBody.class, assertFormattableBody(400, invocation));
    }
}
