/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFalseFluentFuture;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateTrueFluentFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

@ExtendWith(MockitoExtension.class)
class RestconfDataPatchTest extends AbstractRestconfTest {
    @Mock
    private DOMDataTreeReadWriteTransaction tx;

    @BeforeEach
    void beforeEach() {
        doReturn(tx).when(dataBroker).newReadWriteTransaction();
    }

    @Test
    void testPatchData() {
        doNothing().when(tx).delete(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        doReturn(immediateFalseFluentFuture()).when(tx).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doReturn(immediateTrueFluentFuture()).when(tx).exists(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        doReturn(CommitInfo.emptyFluentFuture()).when(tx).commit();
        final var status = assertEntity(PatchStatusContext.class, 200,
            ar -> restconf.dataYangJsonPATCH(stringInputStream("""
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
                }"""), ar));
        assertTrue(status.ok());
        final var edits = status.editCollection();
        assertEquals(3, edits.size());
        assertTrue(edits.get(0).isOk());
        assertTrue(edits.get(1).isOk());
        assertTrue(edits.get(2).isOk());
    }

    @Test
    void testPatchDataDeleteNotExist() {
        doNothing().when(tx).delete(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        doReturn(immediateFalseFluentFuture()).when(tx).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doReturn(immediateFalseFluentFuture()).when(tx).exists(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        doReturn(true).when(tx).cancel();

        final var status = assertEntity(PatchStatusContext.class, 409, ar -> restconf.dataYangJsonPATCH(
            stringInputStream("""
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
                }"""), ar));
        assertFalse(status.ok());
        final var edits = status.editCollection();
        assertEquals(3, edits.size());
        assertTrue(edits.get(0).isOk());
        assertTrue(edits.get(1).isOk());
        final var edit = edits.get(2);
        assertFalse(edit.isOk());
        final var errors = edit.getEditErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("Data does not exist", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, error.getErrorTag());
        assertEquals(GAP_IID, error.getErrorPath());
    }

    @Test
    void testPatchDataMountPoint() throws Exception {
        doNothing().when(tx).delete(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        doReturn(immediateFalseFluentFuture()).when(tx).exists(LogicalDatastoreType.CONFIGURATION, JUKEBOX_IID);
        doReturn(immediateTrueFluentFuture()).when(tx).exists(LogicalDatastoreType.CONFIGURATION, GAP_IID);
        doReturn(CommitInfo.emptyFluentFuture()).when(tx).commit();

        final var status = assertEntity(PatchStatusContext.class, 200,
            ar -> restconf.dataYangXmlPATCH(stringInputStream("""
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
                </yang-patch>"""), ar));
        assertTrue(status.ok());
        assertNull(status.globalErrors());
        final var edits = status.editCollection();
        assertEquals(3, edits.size());
        assertTrue(edits.get(0).isOk());
        assertTrue(edits.get(1).isOk());
        assertTrue(edits.get(2).isOk());
    }
}
