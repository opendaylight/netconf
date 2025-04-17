/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFalseFluentFuture;

import java.util.function.Consumer;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.restconf.server.spi.YangPatchStatusBody;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

class NC1453Test extends AbstractRestconfTest {
    private static final EffectiveModelContext MODEL_CONTEXT = YangParserTestUtils
        .parseYangResourceDirectory("/nc1453");
    private static final YangInstanceIdentifier BAZ_LIST_PATH = YangInstanceIdentifier.of(
        new NodeIdentifier(QName.create("urn:foo", "foo")),
        new NodeIdentifier(QName.create("urn:foo", "baz-list")),
        new NodeWithValue<>(QName.create("urn:foo", "baz-list"), "delta"));
    private static final NormalizedNode BAZ_LIST_NODE = ImmutableNodes.newLeafSetEntryBuilder()
        .withNodeIdentifier(new NodeWithValue<>(QName.create("urn:foo", "baz-list"), "delta"))
        .withValue("delta")
        .build();
    private static final NodeIdentifierWithPredicates BAR_LIST_KEY_IDENTIFIER = NodeIdentifierWithPredicates.of(
        QName.create("urn:foo", "bar-list"),
        QName.create("urn:foo", "bar-key"), "delta");
    private static final YangInstanceIdentifier BAR_LIST_PATH = YangInstanceIdentifier.of(
        new NodeIdentifier(QName.create("urn:foo", "foo")),
        new NodeIdentifier(QName.create("urn:foo", "bar-list")),
        BAR_LIST_KEY_IDENTIFIER);
    private static final NormalizedNode BAR_LIST_NODE = ImmutableNodes.newMapEntryBuilder()
        .withNodeIdentifier(BAR_LIST_KEY_IDENTIFIER)
        .withChild(ImmutableNodes.leafNode(QName.create("urn:foo", "bar-key"), "delta"))
        .build();

    @Mock
    private DOMDataTreeReadWriteTransaction tx;

    @BeforeEach
    void beforeEach() {
        doReturn(tx).when(dataBroker).newReadWriteTransaction();
        doNothing().when(tx).merge(any(), any(), any());
        doReturn(CommitInfo.emptyFluentFuture()).when(tx).commit();
        doReturn(new MultivaluedHashMap<>()).when(uriInfo).getQueryParameters();
    }

    @Override
    EffectiveModelContext modelContext() {
        return MODEL_CONTEXT;
    }

    @Test
    void testMergeLeafList() {
        // Send YANG-PATCH request.
        final var body = assertPatchStatus(200, ar -> restconf.dataYangJsonPATCH(
            stringInputStream("""
                {
                    "ietf-yang-patch:yang-patch": {
                       "patch-id": "patch1",
                       "edit": [
                         {
                           "edit-id": "edit1",
                           "operation": "merge",
                           "target": "/foo:foo/baz-list=delta",
                           "value": {
                             "foo:baz-list": [
                               "delta"
                             ]
                           }
                         }
                       ]
                    }
                }"""), uriInfo, sc, ar));

        // Verify that the correct node with the path is attempted to be stored in the datastore.
        verify(tx).merge(LogicalDatastoreType.CONFIGURATION, BAZ_LIST_PATH, BAZ_LIST_NODE);

        // Verify response.
        assertFormat("""
            {
              "ietf-yang-patch:yang-patch-status": {
                "patch-id": "patch1",
                "ok": [
                  null
                ]
              }
            }""", body::formatToJSON, true);
    }

    @Test
    void testCreateLeafList() {
        doReturn(immediateFalseFluentFuture()).when(tx).exists(any(), any());
        // Send YANG-PATCH request.
        final var body = assertPatchStatus(200, ar -> restconf.dataYangJsonPATCH(
            stringInputStream("""
                {
                    "ietf-yang-patch:yang-patch": {
                       "patch-id": "patch1",
                       "edit": [
                         {
                           "edit-id": "edit1",
                           "operation": "create",
                           "target": "/foo:foo/baz-list=delta",
                           "value": {
                             "foo:baz-list": [
                               "delta"
                             ]
                           }
                         }
                       ]
                    }
                }"""), uriInfo, sc, ar));

        // Verify that the correct node with the path is attempted to be stored in the datastore.
        verify(tx).put(LogicalDatastoreType.CONFIGURATION, BAZ_LIST_PATH, BAZ_LIST_NODE);

        // Verify response.
        assertFormat("""
            {
              "ietf-yang-patch:yang-patch-status": {
                "patch-id": "patch1",
                "ok": [
                  null
                ]
              }
            }""", body::formatToJSON, true);
    }

    @Test
    void testReplaceLeafList() {
        // Send YANG-PATCH request.
        final var body = assertPatchStatus(200, ar -> restconf.dataYangJsonPATCH(
            stringInputStream("""
                {
                    "ietf-yang-patch:yang-patch": {
                       "patch-id": "patch1",
                       "edit": [
                         {
                           "edit-id": "edit1",
                           "operation": "replace",
                           "target": "/foo:foo/baz-list=delta",
                           "value": {
                             "foo:baz-list": [
                               "delta"
                             ]
                           }
                         }
                       ]
                    }
                }"""), uriInfo, sc, ar));

        // Verify that the correct node with the path is attempted to be stored in the datastore.
        verify(tx).put(LogicalDatastoreType.CONFIGURATION, BAZ_LIST_PATH, BAZ_LIST_NODE);

        // Verify response.
        assertFormat("""
            {
              "ietf-yang-patch:yang-patch-status": {
                "patch-id": "patch1",
                "ok": [
                  null
                ]
              }
            }""", body::formatToJSON, true);
    }

    @Test
    void testMergeLeafListXml() {
        // Send YANG-PATCH request.
        final var body = assertPatchStatus(200, ar -> restconf.dataYangXmlPATCH(
            stringInputStream("""
                <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                  <patch-id>patch1</patch-id>
                  <edit>
                    <edit-id>edit1</edit-id>
                    <operation>merge</operation>
                    <target>/foo:foo/baz-list=delta</target>
                    <value>
                      <baz-list xmlns="urn:foo">delta</baz-list>
                    </value>
                  </edit>
                </yang-patch>
            """), uriInfo, sc, ar));

        // Verify that the correct node with the path is attempted to be stored in the datastore.
        verify(tx).merge(LogicalDatastoreType.CONFIGURATION, BAZ_LIST_PATH, BAZ_LIST_NODE);

        // Verify response.
        assertFormat("""
            {
              "ietf-yang-patch:yang-patch-status": {
                "patch-id": "patch1",
                "ok": [
                  null
                ]
              }
            }""", body::formatToJSON, true);
    }

    @Test
    void testCreateLeafListXml() {
        doReturn(immediateFalseFluentFuture()).when(tx).exists(any(), any());
        // Send YANG-PATCH request.
        final var body = assertPatchStatus(200, ar -> restconf.dataYangXmlPATCH(
            stringInputStream("""
                <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                  <patch-id>patch1</patch-id>
                  <edit>
                    <edit-id>edit1</edit-id>
                    <operation>create</operation>
                    <target>/foo:foo/baz-list=delta</target>
                    <value>
                      <baz-list xmlns="urn:foo">delta</baz-list>
                    </value>
                  </edit>
                </yang-patch>
            """), uriInfo, sc, ar));

        // Verify that the correct node with the path is attempted to be stored in the datastore.
        verify(tx).put(LogicalDatastoreType.CONFIGURATION, BAZ_LIST_PATH, BAZ_LIST_NODE);

        // Verify response.
        assertFormat("""
            {
              "ietf-yang-patch:yang-patch-status": {
                "patch-id": "patch1",
                "ok": [
                  null
                ]
              }
            }""", body::formatToJSON, true);
    }

    @Test
    void testReplaceLeafListXml() {
        // Send YANG-PATCH request.
        final var body = assertPatchStatus(200, ar -> restconf.dataYangXmlPATCH(
            stringInputStream("""
                <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                  <patch-id>patch1</patch-id>
                  <edit>
                    <edit-id>edit1</edit-id>
                    <operation>replace</operation>
                    <target>/foo:foo/baz-list=delta</target>
                    <value>
                      <baz-list xmlns="urn:foo">delta</baz-list>
                    </value>
                  </edit>
                </yang-patch>
                """), uriInfo, sc, ar));

        // Verify that the correct node with the path is attempted to be stored in the datastore.
        verify(tx).put(LogicalDatastoreType.CONFIGURATION, BAZ_LIST_PATH, BAZ_LIST_NODE);

        // Verify response.
        assertFormat("""
            {
              "ietf-yang-patch:yang-patch-status": {
                "patch-id": "patch1",
                "ok": [
                  null
                ]
              }
            }""", body::formatToJSON, true);
    }

    @Test
    void testMergeList() {
        // Send YANG-PATCH request.
        final var body = assertPatchStatus(200, ar -> restconf.dataYangJsonPATCH(
            stringInputStream("""
                {
                    "ietf-yang-patch:yang-patch": {
                       "patch-id": "patch1",
                       "edit": [
                         {
                           "edit-id": "edit1",
                           "operation": "merge",
                           "target": "/foo:foo/bar-list=delta",
                           "value": {
                             "foo:bar-list": [
                                {
                                  "bar-key": "delta"
                                }
                             ]
                           }
                         }
                       ]
                    }
                }"""), uriInfo, sc, ar));

        // Verify that the correct node with the path is attempted to be stored in the datastore.
        verify(tx).merge(LogicalDatastoreType.CONFIGURATION, BAR_LIST_PATH, BAR_LIST_NODE);

        // Verify response.
        assertFormat("""
            {
              "ietf-yang-patch:yang-patch-status": {
                "patch-id": "patch1",
                "ok": [
                  null
                ]
              }
            }""", body::formatToJSON, true);
    }

    @Test
    void testCreateList() {
        doReturn(immediateFalseFluentFuture()).when(tx).exists(any(), any());
        // Send YANG-PATCH request.
        final var body = assertPatchStatus(200, ar -> restconf.dataYangJsonPATCH(
            stringInputStream("""
                {
                    "ietf-yang-patch:yang-patch": {
                       "patch-id": "patch1",
                       "edit": [
                         {
                           "edit-id": "edit1",
                           "operation": "create",
                           "target": "/foo:foo/bar-list=delta",
                           "value": {
                             "foo:bar-list": [
                                {
                                  "bar-key": "delta"
                                }
                             ]
                           }
                         }
                       ]
                    }
                }"""), uriInfo, sc, ar));

        // Verify that the correct node with the path is attempted to be stored in the datastore.
        verify(tx).put(LogicalDatastoreType.CONFIGURATION, BAR_LIST_PATH, BAR_LIST_NODE);

        // Verify response.
        assertFormat("""
            {
              "ietf-yang-patch:yang-patch-status": {
                "patch-id": "patch1",
                "ok": [
                  null
                ]
              }
            }""", body::formatToJSON, true);
    }

    @Test
    void testReplaceList() {
        // Send YANG-PATCH request.
        final var body = assertPatchStatus(200, ar -> restconf.dataYangJsonPATCH(
            stringInputStream("""
                {
                    "ietf-yang-patch:yang-patch": {
                       "patch-id": "patch1",
                       "edit": [
                         {
                           "edit-id": "edit1",
                           "operation": "replace",
                           "target": "/foo:foo/bar-list=delta",
                           "value": {
                             "foo:bar-list": [
                                {
                                  "bar-key": "delta"
                                }
                             ]
                           }
                         }
                       ]
                    }
                }"""), uriInfo, sc, ar));

        // Verify that the correct node with the path is attempted to be stored in the datastore.
        verify(tx).put(LogicalDatastoreType.CONFIGURATION, BAR_LIST_PATH, BAR_LIST_NODE);

        // Verify response.
        assertFormat("""
            {
              "ietf-yang-patch:yang-patch-status": {
                "patch-id": "patch1",
                "ok": [
                  null
                ]
              }
            }""", body::formatToJSON, true);
    }

    @Test
    void testMergeListXml() {
        // Send YANG-PATCH request.
        final var body = assertPatchStatus(200, ar -> restconf.dataYangXmlPATCH(
            stringInputStream("""
                <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                  <patch-id>patch1</patch-id>
                  <edit>
                    <edit-id>edit1</edit-id>
                    <operation>merge</operation>
                    <target>/foo:foo/bar-list=delta</target>
                    <value>
                      <bar-list xmlns="urn:foo">
                        <bar-key xmlns="urn:foo">delta</bar-key>
                      </bar-list>
                    </value>
                  </edit>
                </yang-patch>
            """), uriInfo, sc, ar));

        // Verify that the correct node with the path is attempted to be stored in the datastore.
        verify(tx).merge(LogicalDatastoreType.CONFIGURATION, BAR_LIST_PATH, BAR_LIST_NODE);

        // Verify response.
        assertFormat("""
            {
              "ietf-yang-patch:yang-patch-status": {
                "patch-id": "patch1",
                "ok": [
                  null
                ]
              }
            }""", body::formatToJSON, true);
    }

    @Test
    void testCreateListXml() {
        doReturn(immediateFalseFluentFuture()).when(tx).exists(any(), any());
        // Send YANG-PATCH request.
        final var body = assertPatchStatus(200, ar -> restconf.dataYangXmlPATCH(
            stringInputStream("""
                <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                  <patch-id>patch1</patch-id>
                  <edit>
                    <edit-id>edit1</edit-id>
                    <operation>create</operation>
                    <target>/foo:foo/bar-list=delta</target>
                    <value>
                      <bar-list xmlns="urn:foo">
                        <bar-key xmlns="urn:foo">delta</bar-key>
                      </bar-list>
                    </value>
                  </edit>
                </yang-patch>
            """), uriInfo, sc, ar));

        // Verify that the correct node with the path is attempted to be stored in the datastore.
        verify(tx).put(LogicalDatastoreType.CONFIGURATION, BAR_LIST_PATH, BAR_LIST_NODE);

        // Verify response.
        assertFormat("""
            {
              "ietf-yang-patch:yang-patch-status": {
                "patch-id": "patch1",
                "ok": [
                  null
                ]
              }
            }""", body::formatToJSON, true);
    }

    @Test
    void testReplaceListXml() {
        // Send YANG-PATCH request.
        final var body = assertPatchStatus(200, ar -> restconf.dataYangXmlPATCH(
            stringInputStream("""
                <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                  <patch-id>patch1</patch-id>
                  <edit>
                    <edit-id>edit1</edit-id>
                    <operation>replace</operation>
                    <target>/foo:foo/bar-list=delta</target>
                    <value>
                      <bar-list xmlns="urn:foo">
                        <bar-key xmlns="urn:foo">delta</bar-key>
                      </bar-list>
                    </value>
                  </edit>
                </yang-patch>
            """), uriInfo, sc, ar));

        // Verify that the correct node with the path is attempted to be stored in the datastore.
        verify(tx).put(LogicalDatastoreType.CONFIGURATION, BAR_LIST_PATH, BAR_LIST_NODE);

        // Verify response.
        assertFormat("""
            {
              "ietf-yang-patch:yang-patch-status": {
                "patch-id": "patch1",
                "ok": [
                  null
                ]
              }
            }""", body::formatToJSON, true);
    }

    private static YangPatchStatusBody assertPatchStatus(final int status, final Consumer<AsyncResponse> invocation) {
        return assertInstanceOf(YangPatchStatusBody.class, assertFormattableBody(status, invocation));
    }
}
