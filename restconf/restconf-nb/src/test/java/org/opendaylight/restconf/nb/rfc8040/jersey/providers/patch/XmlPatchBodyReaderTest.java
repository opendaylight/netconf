/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.Test;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.nb.rfc8040.databind.XmlPatchBody;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;

public class XmlPatchBodyReaderTest extends AbstractPatchBodyReaderTest {
    @Test
    public final void moduleDataTest() throws Exception {
        checkPatchContext(parse(mountPrefix() + "instance-identifier-patch-module:patch-cont/my-list1=leaf1",
            XmlPatchBodyReaderTest.class.getResourceAsStream("/instanceidentifier/xml/xmlPATCHdata.xml")));
    }

    /**
     * Test trying to use Patch create operation which requires value without value. Error code 400 should be returned.
     */
    @Test
    public final void moduleDataValueMissingNegativeTest() throws Exception {
        final var inputStream = XmlPatchBodyReaderTest.class.getResourceAsStream(
            "/instanceidentifier/xml/xmlPATCHdataValueMissing.xml");
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> parse(mountPrefix() + "instance-identifier-patch-module:patch-cont/my-list1=leaf1", inputStream));
        assertEquals(ErrorTag.MALFORMED_MESSAGE, ex.getErrors().get(0).getErrorTag());
    }

    /**
     * Test trying to use value with Patch delete operation which does not support value. Error code 400 should be
     * returned.
     */
    @Test
    public final void moduleDataNotValueNotSupportedNegativeTest() throws Exception {
        final var inputStream = XmlPatchBodyReaderTest.class.getResourceAsStream(
            "/instanceidentifier/xml/xmlPATCHdataValueNotSupported.xml");
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> parse(mountPrefix() + "instance-identifier-patch-module:patch-cont/my-list1=leaf1", inputStream));
        assertEquals(ErrorTag.MALFORMED_MESSAGE, ex.getErrors().get(0).getErrorTag());
    }

    /**
     * Test of YANG Patch with absolute target path.
     */
    @Test
    public final void moduleDataAbsoluteTargetPathTest() throws Exception {
        checkPatchContext(parse(mountPrefix(), XmlPatchBodyReaderTest.class.getResourceAsStream(
            "/instanceidentifier/xml/xmlPATCHdataAbsoluteTargetPath.xml")));
    }

    /**
     * Test using Patch when target is completely specified in request URI and thus target leaf contains only '/' sign.
     */
    @Test
    public final void modulePatchCompleteTargetInURITest() throws Exception {
        checkPatchContext(parse(mountPrefix() + "instance-identifier-patch-module:patch-cont",
            XmlPatchBodyReaderTest.class.getResourceAsStream(
                "/instanceidentifier/xml/xmlPATCHdataCompleteTargetInURI.xml")));
    }

    /**
     * Test of Yang Patch merge operation on list. Test consists of two edit operations - replace and merge.
     */
    @Test
    public final void moduleDataMergeOperationOnListTest() throws Exception {
        checkPatchContext(parse(mountPrefix() + "instance-identifier-patch-module:patch-cont/my-list1=leaf1",
            XmlPatchBodyReaderTest.class.getResourceAsStream(
                "/instanceidentifier/xml/xmlPATCHdataMergeOperationOnList.xml")));
    }

    /**
     * Test of Yang Patch merge operation on container. Test consists of two edit operations - create and merge.
     */
    @Test
    public final void moduleDataMergeOperationOnContainerTest() throws Exception {
        checkPatchContext(parse(mountPrefix() + "instance-identifier-patch-module:patch-cont",
            XmlPatchBodyReaderTest.class.getResourceAsStream(
                "/instanceidentifier/xml/xmlPATCHdataMergeOperationOnContainer.xml")));
    }

    /**
     * Test of Yang Patch on the top-level container with empty URI for data root.
     */
    @Test
    public final void modulePatchTargetTopLevelContainerWithEmptyURITest() throws Exception {
        checkPatchContext(parse(mountPrefix(),
            XmlPatchBodyReaderTest.class.getResourceAsStream(
                "/instanceidentifier/xml/xmlPATCHTargetTopLevelContainerWithEmptyURI.xml")));
    }

    /**
     * Test of YANG Patch on the top-level container with the full path in the URI and "/" in 'target'.
     */
    @Test
    public final void modulePatchTargetTopLevelContainerWithFullPathURITest() throws Exception {
        final var returnValue = parse(mountPrefix() + "instance-identifier-patch-module:patch-cont", """
            <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                <patch-id>test-patch</patch-id>
                <comment>Test patch applied to the top-level container with '/' in target</comment>
                <edit>
                    <edit-id>edit1</edit-id>
                    <operation>replace</operation>
                    <target>/</target>
                    <value>
                        <patch-cont xmlns="instance:identifier:patch:module">
                            <my-list1>
                                <name>my-leaf-set</name>
                                <my-leaf11>leaf-a</my-leaf11>
                                <my-leaf12>leaf-b</my-leaf12>
                            </my-list1>
                        </patch-cont>
                    </value>
                </edit>
            </yang-patch>""");
        checkPatchContext(returnValue);
        assertEquals(Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(PATCH_CONT_QNAME))
            .withChild(Builders.mapBuilder()
                .withNodeIdentifier(new NodeIdentifier(MY_LIST1_QNAME))
                .withChild(Builders.mapEntryBuilder()
                    .withNodeIdentifier(NodeIdentifierWithPredicates.of(MY_LIST1_QNAME, LEAF_NAME_QNAME, "my-leaf-set"))
                    .withChild(ImmutableNodes.leafNode(LEAF_NAME_QNAME, "my-leaf-set"))
                    .withChild(ImmutableNodes.leafNode(MY_LEAF11_QNAME, "leaf-a"))
                    .withChild(ImmutableNodes.leafNode(MY_LEAF12_QNAME, "leaf-b"))
                    .build())
                .build())
            .build(), returnValue.getData().get(0).getNode());
    }

    /**
     * Test of YANG Patch on the second-level list with the full path in the URI and "/" in 'target'.
     */
    @Test
    public final void modulePatchTargetSecondLevelListWithFullPathURITest() throws Exception {
        final var returnValue = parse(
            mountPrefix() + "instance-identifier-patch-module:patch-cont/my-list1=my-leaf-set",
            """
                <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                    <patch-id>test-patch</patch-id>
                    <comment>Test patch applied to the second-level list with '/' in target</comment>
                    <edit>
                        <edit-id>edit1</edit-id>
                        <operation>replace</operation>
                        <target>/</target>
                        <value>
                            <my-list1 xmlns="instance:identifier:patch:module">
                                <name>my-leaf-set</name>
                                <my-leaf11>leaf-a</my-leaf11>
                                <my-leaf12>leaf-b</my-leaf12>
                            </my-list1>
                        </value>
                    </edit>
                </yang-patch>""");
        checkPatchContext(returnValue);
        assertEquals(Builders.mapBuilder()
            .withNodeIdentifier(new NodeIdentifier(MY_LIST1_QNAME))
            .withChild(Builders.mapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(MY_LIST1_QNAME, LEAF_NAME_QNAME, "my-leaf-set"))
                .withChild(ImmutableNodes.leafNode(LEAF_NAME_QNAME, "my-leaf-set"))
                .withChild(ImmutableNodes.leafNode(MY_LEAF11_QNAME, "leaf-a"))
                .withChild(ImmutableNodes.leafNode(MY_LEAF12_QNAME, "leaf-b"))
                .build())
            .build(), returnValue.getData().get(0).getNode());
    }

    /**
     * Test of Yang Patch on the top augmented element.
     */
    @Test
    public final void moduleTargetTopLevelAugmentedContainerTest() throws Exception {
        final var returnValue = parse(mountPrefix(), """
            <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                <patch-id>test-patch</patch-id>
                <comment>This test patch for augmented element</comment>
                <edit>
                    <edit-id>edit1</edit-id>
                    <operation>replace</operation>
                    <target>/test-m:container-root/test-m:container-lvl1/test-m-aug:container-aug</target>
                    <value>
                        <container-aug xmlns="test-ns-aug">
                            <leaf-aug>data</leaf-aug>
                        </container-aug>
                    </value>
                </edit>
            </yang-patch>""");
        checkPatchContext(returnValue);
        assertEquals(Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(CONT_AUG_QNAME))
            .withChild(ImmutableNodes.leafNode(LEAF_AUG_QNAME, "data"))
            .build(), returnValue.getData().get(0).getNode());
    }

    /**
     * Test of YANG Patch on the top system map node element.
     */
    @Test
    public final void moduleTargetMapNodeTest() throws Exception {
        final var returnValue = parse(mountPrefix(), """
            <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                <patch-id>map-patch</patch-id>
                <comment>YANG patch comment</comment>
                <edit>
                    <edit-id>edit1</edit-id>
                    <operation>replace</operation>
                    <target>/map-model:cont-root/map-model:cont1/map-model:my-map=key</target>
                    <value>
                        <my-map xmlns="map:ns">
                            <key-leaf>key</key-leaf>
                            <data-leaf>data</data-leaf>
                        </my-map>
                    </value>
                </edit>
            </yang-patch>""");
        checkPatchContext(returnValue);
        assertEquals(Builders.mapBuilder()
            .withNodeIdentifier(new NodeIdentifier(MAP_CONT_QNAME))
            .withChild(Builders.mapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(MAP_CONT_QNAME, KEY_LEAF_QNAME, "key"))
                .withChild(ImmutableNodes.leafNode(KEY_LEAF_QNAME, "key"))
                .withChild(ImmutableNodes.leafNode(DATA_LEAF_QNAME, "data"))
                .build())
            .build(), returnValue.getData().get(0).getNode());
    }

    /**
     * Test of YANG Patch on the leaf set node element.
     */
    @Test
    public final void modulePatchTargetLeafSetNodeTest() throws Exception {
        final var returnValue = parse(mountPrefix(), """
            <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                <patch-id>set-patch</patch-id>
                <comment>YANG patch comment</comment>
                <edit>
                    <edit-id>edit1</edit-id>
                    <operation>replace</operation>
                    <target>/set-model:cont-root/set-model:cont1/set-model:my-set="data1"</target>
                    <value>
                        <my-set xmlns="set:ns">data1</my-set>
                    </value>
                </edit>
            </yang-patch>""");
        checkPatchContext(returnValue);
        assertEquals(Builders.leafSetBuilder()
            .withNodeIdentifier(new NodeIdentifier(LEAF_SET_QNAME))
            .withChild(Builders.leafSetEntryBuilder()
                .withNodeIdentifier(new NodeWithValue<>(LEAF_SET_QNAME, "data1"))
                .withValue("data1")
                .build())
            .build(), returnValue.getData().get(0).getNode());
    }

    /**
     * Test of Yang Patch on the top unkeyed list element.
     */
    @Test
    public final void moduleTargetUnkeyedListNodeTest() throws Exception {
        final var returnValue = parse(mountPrefix(), """
            <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                <patch-id>list-patch</patch-id>
                <comment>YANG patch comment</comment>
                <edit>
                    <edit-id>edit1</edit-id>
                    <operation>replace</operation>
                    <target>/list-model:cont-root/list-model:cont1/list-model:unkeyed-list</target>
                    <value>
                        <unkeyed-list xmlns="list:ns">
                            <leaf1>data1</leaf1>
                            <leaf2>data2</leaf2>
                        </unkeyed-list>
                    </value>
                </edit>
            </yang-patch>""");
        checkPatchContext(returnValue);
        assertEquals(Builders.unkeyedListBuilder()
            .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
            .withChild(Builders.unkeyedListEntryBuilder()
                .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
                .withChild(ImmutableNodes.leafNode(LIST_LEAF1_QNAME, "data1"))
                .withChild(ImmutableNodes.leafNode(LIST_LEAF2_QNAME, "data2"))
                .build())
            .build(), returnValue.getData().get(0).getNode());
    }

    /**
     * Test of Yang Patch on the top case node element.
     */
    @Test
    public final void moduleTargetCaseNodeTest() throws Exception {
        final var returnValue = parse(mountPrefix(), """
            <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                <patch-id>choice-patch</patch-id>
                <comment>YANG patch comment</comment>
                <edit>
                    <edit-id>edit1</edit-id>
                    <operation>replace</operation>
                    <target>/choice-model:cont-root/choice-model:cont1/choice-model:case-cont1</target>
                    <value>
                        <case-cont1 xmlns="choice:ns">
                            <case-leaf1>data</case-leaf1>
                        </case-cont1>
                    </value>
                </edit>
            </yang-patch>""");
        checkPatchContext(returnValue);
        assertEquals(Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(CHOICE_CONT_QNAME))
            .withChild(ImmutableNodes.leafNode(CASE_LEAF1_QNAME, "data"))
            .build(), returnValue.getData().get(0).getNode());
    }

    /**
     * Test reading simple leaf value.
     */
    @Test
    public final void modulePatchSimpleLeafValueTest() throws Exception {
        final var returnValue = parse(mountPrefix() + "instance-identifier-patch-module:patch-cont/my-list1=leaf1", """
            <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                <patch-id>test-patch</patch-id>
                <comment>this is test patch</comment>
                <edit>
                    <edit-id>edit1</edit-id>
                    <operation>replace</operation>
                    <target>/my-list2=my-leaf20/name</target>
                    <value>
                        <name xmlns="instance:identifier:patch:module">my-leaf20</name>
                    </value>
                </edit>
            </yang-patch>""");
        checkPatchContext(returnValue);
        assertEquals(ImmutableNodes.leafNode(LEAF_NAME_QNAME, "my-leaf20"), returnValue.getData().get(0).getNode());
    }

    private @NonNull PatchContext parse(final String uriPath, final String patchBody) throws IOException {
        return parse(uriPath, stringInputStream(patchBody));
    }

    // FIXME: migrate callers to use the above instead of resources
    @Deprecated
    private @NonNull PatchContext parse(final String uriPath, final InputStream patchBody) throws IOException {
        return new XmlPatchBody(patchBody).toPatchContext(
            ParserIdentifier.toInstanceIdentifier(uriPath, IID_SCHEMA, mountPointService));
    }
}
