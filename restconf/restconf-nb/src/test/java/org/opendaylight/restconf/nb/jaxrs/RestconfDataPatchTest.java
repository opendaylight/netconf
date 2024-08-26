/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFalseFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateTrueFluentFuture;

import java.util.Map;
import java.util.function.Consumer;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.spi.YangPatchStatusBody;

@ExtendWith(MockitoExtension.class)
class RestconfDataPatchTest extends AbstractRestconfTest {
    @Mock
    private DOMDataTreeReadWriteTransaction tx;
    @Mock
    private UriInfo uriInfo;

    @BeforeEach
    void beforeEach() {
        doReturn(tx).when(dataBroker).newReadWriteTransaction();
        doReturn(new MultivaluedHashMap<>(Map.of(PrettyPrintParam.uriName, "true"))).when(uriInfo).getQueryParameters();
    }

    @Test
    void testPatchData() {
        doNothing().when(tx).delete(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        doReturn(immediateFalseFluentFuture()).when(tx).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doReturn(immediateTrueFluentFuture()).when(tx).exists(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        doReturn(CommitInfo.emptyFluentFuture()).when(tx).commit();

        final var body = assertPatchStatus(200, ar -> restconf.dataYangJsonPATCH(stringInputStream("""
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
                    }
                  },
                  {
                    "edit-id" : "replace data",
                    "operation" : "replace",
                    "target" : "/example-jukebox:jukebox",
                    "value" : {
                      "jukebox" : {
                        "player" : {
                          "gap" : "0.3"
                        }
                      }
                    }
                  },
                  {
                    "edit-id" : "delete data",
                    "operation" : "delete",
                    "target" : "/example-jukebox:jukebox/player/gap"
                  }
                ]
              }
            }"""), uriInfo, ar));

        assertFormat("""
            {
              "ietf-yang-patch:yang-patch-status": {
                "patch-id": "test patch id",
                "ok": [
                  null
                ]
              }
            }""", body::formatToJSON, true);
    }

    @Test
    void testPatchDataDeleteNotExist() {
        doNothing().when(tx).delete(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        doReturn(immediateFalseFluentFuture()).when(tx).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doReturn(immediateFalseFluentFuture()).when(tx).exists(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        doReturn(true).when(tx).cancel();

        final var body = assertPatchStatus(409, ar -> restconf.dataYangJsonPATCH( stringInputStream("""
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
                    }
                  },
                  {
                    "edit-id" : "remove data",
                    "operation" : "remove",
                    "target" : "/example-jukebox:jukebox/player/gap"
                  },
                  {
                    "edit-id" : "delete data",
                    "operation" : "delete",
                    "target" : "/example-jukebox:jukebox/player/gap"
                  }
                ]
              }
            }"""), uriInfo, ar));

        assertFormat("""
            {
              "ietf-yang-patch:yang-patch-status": {
                "patch-id": "test patch id",
                "edit-status": {
                  "edit": [
                    {
                      "edit-id": "create data",
                      "ok": [
                        null
                      ]
                    },
                    {
                      "edit-id": "remove data",
                      "ok": [
                        null
                      ]
                    },
                    {
                      "edit-id": "delete data",
                      "errors": {
                        "error": [
                          {
                            "error-type": "protocol",
                            "error-tag": "data-missing",
                            "error-path": "/example-jukebox:jukebox/player/gap",
                            "error-message": "Data does not exist"
                          }
                        ]
                      }
                    }
                  ]
                }
              }
            }""", body::formatToJSON, true);
    }

    @Test
    void testPatchDataMountPoint() throws Exception {
        doNothing().when(tx).delete(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        doReturn(immediateFalseFluentFuture()).when(tx).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doReturn(immediateTrueFluentFuture()).when(tx).exists(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        doReturn(CommitInfo.emptyFluentFuture()).when(tx).commit();

        final var body = assertPatchStatus(200, ar -> restconf.dataYangXmlPATCH(stringInputStream("""
            <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
              <patch-id>test patch id</patch-id>
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
              <edit>
                <edit-id>replace data</edit-id>
                <operation>replace</operation>
                <target>/example-jukebox:jukebox</target>
                <value>
                  <jukebox xmlns="http://example.com/ns/example-jukebox">
                    <player>
                      <gap>0.3</gap>
                    </player>
                  </jukebox>
                </value>
              </edit>
              <edit>
                <edit-id>delete data</edit-id>
                <operation>delete</operation>
                <target>/example-jukebox:jukebox/player/gap</target>
              </edit>
            </yang-patch>"""), uriInfo, ar));

        assertFormat("""
            <yang-patch-status xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
              <patch-id>test patch id</patch-id>
              <ok/>
            </yang-patch-status>""", body::formatToXML, true);
    }

    private static YangPatchStatusBody assertPatchStatus(final int status, final Consumer<AsyncResponse> invocation) {
        return assertInstanceOf(YangPatchStatusBody.class, assertFormattableBody(status, invocation));
    }
}
