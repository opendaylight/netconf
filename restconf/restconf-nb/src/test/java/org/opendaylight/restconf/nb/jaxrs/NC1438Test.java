/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;

import java.util.Map;
import javax.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

@ExtendWith(MockitoExtension.class)
class NC1438Test extends AbstractRestconfTest {

    @BeforeEach
    void beforeEach() {
        doReturn(new MultivaluedHashMap<>(Map.of(PrettyPrintParam.uriName, "true"))).when(uriInfo).getQueryParameters();
    }

    @Test
    void testPatchWithMultipleValues() {
        final var restconfError = assertError(ar -> restconf.dataYangJsonPATCH(stringInputStream("""
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
            }"""), uriInfo, ar));

        assertEquals("Multiple value entries found", restconfError.getErrorMessage());
        assertEquals(ErrorTag.MALFORMED_MESSAGE, restconfError.getErrorTag());
        assertEquals(ErrorType.APPLICATION, restconfError.getErrorType());
    }

    @Test
    void testPatchWithMultipleValuesAndDeferredUsed() {
        final var restconfError = assertError(ar -> restconf.dataYangJsonPATCH(stringInputStream("""
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
            }"""), uriInfo, ar));
        assertEquals("Multiple value entries found", restconfError.getErrorMessage());
        assertEquals(ErrorTag.MALFORMED_MESSAGE, restconfError.getErrorTag());
        assertEquals(ErrorType.APPLICATION, restconfError.getErrorType());
    }

    @Test
    void testXmlPatchMissingEditIdData() {
        final var restconfError = assertError(ar -> restconf.dataYangXmlPATCH(stringInputStream("""
            <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
              <patch-id>test patch id</patch-id>
              <edit>
                <operation>create</operation>
                <target>/example-jukebox:jukebox</target>
                <value>
                  <jukebox xmlns="http://example.com/ns/example-jukebox">
                    <player>
                      <gap>0.2</gap>
                    </player>
                  </jukebox>
                </value>
              </edit>
            </yang-patch>"""), uriInfo, ar));

        assertEquals("Missing required element 'edit-id'", restconfError.getErrorMessage());
        assertEquals(ErrorTag.MISSING_ELEMENT, restconfError.getErrorTag());
        assertEquals(ErrorType.APPLICATION, restconfError.getErrorType());
    }

    @Test
    void testPatchMissingEditIdData() {
        final var restconfError = assertError(ar -> restconf.dataYangJsonPATCH(stringInputStream("""
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
            }"""), uriInfo, ar));

        assertEquals("Missing required element 'edit-id'", restconfError.getErrorMessage());
        assertEquals(ErrorTag.MISSING_ELEMENT, restconfError.getErrorTag());
        assertEquals(ErrorType.APPLICATION, restconfError.getErrorType());
    }

    @Test
    void testXmlPatchMissingPatchIdData() {
        final var restconfError = assertError(ar -> restconf.dataYangXmlPATCH(stringInputStream("""
            <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
              <edit>
                <edit-id>create data</edit-id>
                <operation>create</operation>
                <target>/example-jukebox:jukebox</target>
                <value>
                  <jukebox xmlns="http://example.com/ns/example-jukebox">
                    <player>
                      <gap>0.2</gap>
                    </player>
                  </jukebox>
                </value>
              </edit>
            </yang-patch>"""), uriInfo, ar));

        assertEquals("Missing required element 'patch-id'", restconfError.getErrorMessage());
        assertEquals(ErrorTag.MISSING_ELEMENT, restconfError.getErrorTag());
        assertEquals(ErrorType.APPLICATION, restconfError.getErrorType());
    }

    @Test
    void testPatchMissingPatchIdData() {
        final var restconfError = assertError(ar -> restconf.dataYangJsonPATCH(stringInputStream("""
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
            }"""), uriInfo, ar));

        assertEquals("Missing required element 'patch-id'", restconfError.getErrorMessage());
        assertEquals(ErrorTag.MISSING_ELEMENT, restconfError.getErrorTag());
        assertEquals(ErrorType.APPLICATION, restconfError.getErrorType());
    }

    @Test
    void testXmlPatchMissingOperation() {
        final var restconfError = assertError(ar -> restconf.dataYangXmlPATCH(stringInputStream("""
            <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
              <patch-id>test patch id</patch-id>
              <edit>
                <edit-id>create data</edit-id>
                <target>/example-jukebox:jukebox</target>
                <value>
                  <jukebox xmlns="http://example.com/ns/example-jukebox">
                    <player>
                      <gap>0.2</gap>
                    </player>
                  </jukebox>
                </value>
              </edit>
            </yang-patch>"""), uriInfo, ar));

        assertEquals("Missing required element 'operation'", restconfError.getErrorMessage());
        assertEquals(ErrorTag.MISSING_ELEMENT, restconfError.getErrorTag());
        assertEquals(ErrorType.APPLICATION, restconfError.getErrorType());
    }

    @Test
    void testPatchMissingOperation() {
        final var restconfError = assertError(ar -> restconf.dataYangJsonPATCH(stringInputStream("""
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
            }"""), uriInfo, ar));

        assertEquals("Missing required element 'operation'", restconfError.getErrorMessage());
        assertEquals(ErrorTag.MISSING_ELEMENT, restconfError.getErrorTag());
        assertEquals(ErrorType.APPLICATION, restconfError.getErrorType());
    }

    @Test
    void testXmlPatchMissingTarget() {
        final var restconfError = assertError(ar -> restconf.dataYangXmlPATCH(stringInputStream("""
            <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
              <patch-id>test patch id</patch-id>
              <edit>
                <edit-id>create data</edit-id>
                <operation>create</operation>
                <value>
                  <jukebox xmlns="http://example.com/ns/example-jukebox">
                    <player>
                      <gap>0.2</gap>
                    </player>
                  </jukebox>
                </value>
              </edit>
            </yang-patch>"""), uriInfo, ar));

        assertEquals("Missing required element 'target'", restconfError.getErrorMessage());
        assertEquals(ErrorTag.MISSING_ELEMENT, restconfError.getErrorTag());
        assertEquals(ErrorType.APPLICATION, restconfError.getErrorType());
    }

    @Test
    void testPatchMissingTarget() {
        final var restconfError = assertError(ar -> restconf.dataYangJsonPATCH(stringInputStream("""
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
            }"""), uriInfo, ar));

        assertEquals("Missing required element 'target'", restconfError.getErrorMessage());
        assertEquals(ErrorTag.MISSING_ELEMENT, restconfError.getErrorTag());
        assertEquals(ErrorType.APPLICATION, restconfError.getErrorType());
    }

    @Test
    void testXmlPatchWrongOperationData() {
        final var restconfError = assertError(ar -> restconf.dataYangXmlPATCH(stringInputStream("""
            <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
              <patch-id>test patch id</patch-id>
              <edit>
                <edit-id>create data</edit-id>
                <operation>WRONG</operation>
                <target>/example-jukebox:jukebox</target>
                <value>
                  <jukebox xmlns="http://example.com/ns/example-jukebox">
                    <player>
                      <gap>0.2</gap>
                    </player>
                  </jukebox>
                </value>
              </edit>
            </yang-patch>"""), uriInfo, ar));

        assertEquals("Operation value is incorrect: \"WRONG\" is not a valid name", restconfError.getErrorMessage());
        assertEquals("\"WRONG\" is not a valid name", restconfError.getErrorInfo());
        assertEquals(ErrorTag.INVALID_VALUE, restconfError.getErrorTag());
        assertEquals(ErrorType.APPLICATION, restconfError.getErrorType());
    }

    @Test
    void testPatchWrongOperationData() {
        final var restconfError = assertError(ar -> restconf.dataYangJsonPATCH(stringInputStream("""
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
            }"""), uriInfo, ar));

        assertEquals("Operation value is incorrect: \"wrong\" is not a valid name", restconfError.getErrorMessage());
        assertEquals("\"wrong\" is not a valid name", restconfError.getErrorInfo());
        assertEquals(ErrorTag.INVALID_VALUE, restconfError.getErrorTag());
        assertEquals(ErrorType.APPLICATION, restconfError.getErrorType());
    }

    @Test
    @SuppressWarnings("checkstyle:LineLength")
    void testXmlPatchWrongLeafData() {
        final var restconfError = assertError(ar -> restconf.dataYangXmlPATCH(stringInputStream("""
            <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
              <patch-id>test patch id</patch-id>
              <edit>
                <edit-id>create data</edit-id>
                <operation>create</operation>
                <target>/example-jukebox:jukebox</target>
                <value>
                  <jukebox xmlns="http://example.com/ns/example-jukebox">
                    <player>
                      <WRONG>0.2</WRONG>
                    </player>
                  </jukebox>
                </value>
              </edit>
            </yang-patch>"""), uriInfo, ar));

        assertEquals("Error parsing YANG Patch XML: ParseError at [row,col]:[-1,-1]\nMessage: Schema for node"
                + " with name WRONG and namespace http://example.com/ns/example-jukebox does not exist in parent EmptyContainerEffectiveStatement{argument=(http://example.com/ns/example-jukebox?revision=2015-04-04)player}",
            restconfError.getErrorMessage());
        assertEquals(ErrorTag.MALFORMED_MESSAGE, restconfError.getErrorTag());
        assertEquals(ErrorType.PROTOCOL, restconfError.getErrorType());
    }

    @Test
    void testPatchWrongLeafData() {
        final var restconfError = assertError(ar -> restconf.dataYangJsonPATCH(stringInputStream("""
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
            }"""), uriInfo, ar));

        assertEquals("Schema node with name wrong was not found under"
            + " (http://example.com/ns/example-jukebox?revision=2015-04-04)player.", restconfError.getErrorMessage());
        assertEquals(ErrorTag.MALFORMED_MESSAGE, restconfError.getErrorTag());
        assertEquals(ErrorType.APPLICATION, restconfError.getErrorType());
    }

    @Test
    void testPatchWrongEditSchemaNode() {
        final var restconfError = assertError(ar -> restconf.dataYangJsonPATCH(stringInputStream("""
            {
              "ietf-yang-patch:yang-patch" : {
                "patch-id" : "test patch id",
                "edit" : [
                  {
                    "wrong" : "create data",
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
            }"""), uriInfo, ar));

        assertEquals("Provided unknown element 'wrong'", restconfError.getErrorMessage());
        assertEquals(ErrorTag.UNKNOWN_ELEMENT, restconfError.getErrorTag());
        assertEquals(ErrorType.APPLICATION, restconfError.getErrorType());
    }

    @Test
    void testPatchEmptyValue() {
        final var restconfError = assertError(ar -> restconf.dataYangJsonPATCH(stringInputStream("""
            {
              "ietf-yang-patch:yang-patch" : {
                "patch-id" : "test patch id",
                "edit" : [
                  {
                    "edit-id" : "create data",
                    "operation" : "create",
                    "target" : "/example-jukebox:jukebox",
                    "value" : {
                    }
                  }
                ]
              }
            }"""), uriInfo, ar));

        assertEquals("Empty 'value' element is not allowed", restconfError.getErrorMessage());
        assertEquals(ErrorTag.INVALID_VALUE, restconfError.getErrorTag());
        assertEquals(ErrorType.APPLICATION, restconfError.getErrorType());
    }

    @Test
    void testXmlPatchEmptyValue() {
        final var restconfError = assertError(ar -> restconf.dataYangXmlPATCH(stringInputStream("""
            <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
              <patch-id>test patch id</patch-id>
              <edit>
                <edit-id>create data</edit-id>
                <operation>create</operation>
                <target>/example-jukebox:jukebox</target>
                <value>
                </value>
              </edit>
            </yang-patch>"""), uriInfo, ar));

        assertEquals("Empty 'value' element is not allowed", restconfError.getErrorMessage());
        assertEquals(ErrorTag.INVALID_VALUE, restconfError.getErrorTag());
        assertEquals(ErrorType.APPLICATION, restconfError.getErrorType());
    }

    @Test
    void testPatchEmptyDeferredValue() {
        final var restconfError = assertError(ar -> restconf.dataYangJsonPATCH(stringInputStream("""
            {
              "ietf-yang-patch:yang-patch" : {
                "patch-id" : "test patch id",
                "edit" : [
                  {
                    "edit-id" : "create data",
                    "operation" : "create",
                    "value" : {
                    }
                    "target" : "/example-jukebox:jukebox",
                  }
                ]
              }
            }"""), uriInfo, ar));

        assertEquals("Empty 'value' element is not allowed", restconfError.getErrorMessage());
        assertEquals(ErrorTag.INVALID_VALUE, restconfError.getErrorTag());
        assertEquals(ErrorType.APPLICATION, restconfError.getErrorType());
    }
}
