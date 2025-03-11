/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.server.api.XmlPatchBody;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

class XmlPatchBodyTest extends AbstractPatchBodyTest {
    XmlPatchBodyTest() {
        super(XmlPatchBody::new);
    }

    @Test
    final void moduleDataTest() throws Exception {
        checkPatchContext(parse(mountPrefix(), "instance-identifier-patch-module:patch-cont/my-list1=leaf1", """
            <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                <patch-id>test-patch</patch-id>
                <comment>this is test patch</comment>
                <edit>
                    <edit-id>edit1</edit-id>
                    <operation>create</operation>
                    <target>/my-list2=my-leaf20</target>
                    <value>
                        <my-list2 xmlns="instance:identifier:patch:module">
                            <name>my-leaf20</name>
                            <my-leaf21>I am leaf21-0</my-leaf21>
                            <my-leaf22>I am leaf22-0</my-leaf22>
                        </my-list2>
                    </value>
                </edit>
                <edit>
                    <edit-id>edit2</edit-id>
                    <operation>create</operation>
                    <target>/my-list2=my-leaf21</target>
                    <value>
                        <my-list2 xmlns="instance:identifier:patch:module">
                            <name>my-leaf21</name>
                            <my-leaf21>I am leaf21-1</my-leaf21>
                            <my-leaf22>I am leaf22-1</my-leaf22>
                        </my-list2>
                    </value>
                </edit>
            </yang-patch>"""));
    }

    /**
     * Test trying to use Patch create operation which requires value without value. Error code 400 should be returned.
     */
    @Test
    final void moduleDataValueMissingNegativeTest() {
        final var ex = assertThrows(RequestException.class,
            () -> parse(mountPrefix(), "instance-identifier-patch-module:patch-cont/my-list1=leaf1", """
                <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                    <patch-id>test-patch</patch-id>
                    <comment>Test patch with missing value node for create operation</comment>
                    <edit>
                        <edit-id>edit1</edit-id>
                        <operation>create</operation>
                        <target>/my-list2</target>
                    </edit>
                </yang-patch>"""));
        final var requestError = ex.errors().get(0);
        assertEquals(ErrorTag.INVALID_VALUE, requestError.tag());
        assertEquals(ErrorType.APPLICATION, requestError.type());
        assertEquals("Create operation requires 'value' element", requestError.message().elementBody());
    }

    /**
     * Test trying to use value with Patch delete operation which does not support value. Error code 400 should be
     * returned.
     */
    @Test
    final void moduleDataNotValueNotSupportedNegativeTest() {
        final var ex = assertThrows(RequestException.class,
            () -> parse(mountPrefix(), "instance-identifier-patch-module:patch-cont/my-list1=leaf1", """
                <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                    <patch-id>test-patch</patch-id>
                    <comment>Test patch with not allowed value node for delete operation</comment>
                    <edit>
                        <edit-id>edit1</edit-id>
                        <operation>delete</operation>
                        <target>/my-list2/my-leaf21</target>
                        <value>
                            <my-list2 xmlns="instance:identifier:patch:module">
                                <name>my-leaf20</name>
                                <my-leaf21>I am leaf21-0</my-leaf21>
                                <my-leaf22>I am leaf22-0</my-leaf22>
                            </my-list2>
                        </value>
                    </edit>
                </yang-patch>"""));
        final var requestError = ex.errors().get(0);
        assertEquals(ErrorTag.INVALID_VALUE, requestError.tag());
        assertEquals(ErrorType.APPLICATION, requestError.type());
        assertEquals("Delete operation can not have 'value' element", requestError.message().elementBody());
    }

    /**
     * Test of YANG Patch with absolute target path.
     */
    @Test
    final void moduleDataAbsoluteTargetPathTest() throws Exception {
        checkPatchContext(parse(mountPrefix(), "", """
            <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                <patch-id>test-patch</patch-id>
                <comment>Test patch with absolute target path</comment>
                <edit>
                    <edit-id>edit1</edit-id>
                    <operation>create</operation>
                    <target>/instance-identifier-patch-module:patch-cont/my-list1=leaf1/my-list2=my-leaf20</target>
                    <value>
                        <my-list2 xmlns="instance:identifier:patch:module">
                            <name>my-leaf20</name>
                            <my-leaf21>I am leaf21-0</my-leaf21>
                            <my-leaf22>I am leaf22-0</my-leaf22>
                        </my-list2>
                    </value>
                </edit>
                <edit>
                    <edit-id>edit2</edit-id>
                    <operation>create</operation>
                    <target>/instance-identifier-patch-module:patch-cont/my-list1=leaf1/my-list2=my-leaf21</target>
                    <value>
                        <my-list2 xmlns="instance:identifier:patch:module">
                            <name>my-leaf21</name>
                            <my-leaf21>I am leaf21-1</my-leaf21>
                            <my-leaf22>I am leaf22-1</my-leaf22>
                        </my-list2>
                    </value>
                </edit>
            </yang-patch>"""));
    }

    /**
     * Test using Patch when target is completely specified in request URI and thus target leaf contains only '/' sign.
     */
    @Test
    final void modulePatchCompleteTargetInURITest() throws Exception {
        checkPatchContext(parse(mountPrefix(), "instance-identifier-patch-module:patch-cont", """
            <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                <patch-id>test-patch</patch-id>
                <comment>Test to create and replace data in container directly using / sign as a target</comment>
                <edit>
                    <edit-id>edit1</edit-id>
                    <operation>create</operation>
                    <target>/</target>
                    <value>
                        <patch-cont xmlns="instance:identifier:patch:module">
                            <my-list1>
                                <name>my-list1 - A</name>
                                <my-leaf11>I am leaf11-0</my-leaf11>
                                <my-leaf12>I am leaf12-1</my-leaf12>
                            </my-list1>
                            <my-list1>
                                <name>my-list1 - B</name>
                                <my-leaf11>I am leaf11-0</my-leaf11>
                                <my-leaf12>I am leaf12-1</my-leaf12>
                            </my-list1>
                        </patch-cont>
                    </value>
                </edit>
                <edit>
                    <edit-id>edit2</edit-id>
                    <operation>replace</operation>
                    <target>/</target>
                    <value>
                        <patch-cont xmlns="instance:identifier:patch:module">
                            <my-list1>
                                <name>my-list1 - Replacing</name>
                                <my-leaf11>I am leaf11-0</my-leaf11>
                                <my-leaf12>I am leaf12-1</my-leaf12>
                            </my-list1>
                        </patch-cont>
                    </value>
                </edit>
            </yang-patch>"""));
    }

    /**
     * Test of Yang Patch merge operation on list. Test consists of two edit operations - replace and merge.
     */
    @Test
    final void moduleDataMergeOperationOnListTest() throws Exception {
        checkPatchContext(parse(mountPrefix(), "instance-identifier-patch-module:patch-cont/my-list1=leaf1", """
            <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                <patch-id>Test merge operation</patch-id>
                <comment>This is test patch for merge operation on list</comment>
                <edit>
                    <edit-id>edit1</edit-id>
                    <operation>replace</operation>
                    <target>/my-list2=my-leaf20</target>
                    <value>
                        <my-list2 xmlns="instance:identifier:patch:module">
                            <name>my-leaf20</name>
                            <my-leaf21>I am leaf21-0</my-leaf21>
                            <my-leaf22>I am leaf22-0</my-leaf22>
                        </my-list2>
                    </value>
                </edit>
                <edit>
                    <edit-id>edit2</edit-id>
                    <operation>merge</operation>
                    <target>/my-list2=my-leaf21</target>
                    <value>
                        <my-list2 xmlns="instance:identifier:patch:module">
                            <name>my-leaf21</name>
                            <my-leaf21>I am leaf21-1</my-leaf21>
                            <my-leaf22>I am leaf22-1</my-leaf22>
                        </my-list2>
                    </value>
                </edit>
            </yang-patch>"""));
    }

    /**
     * Test of Yang Patch merge operation on container. Test consists of two edit operations - create and merge.
     */
    @Test
    final void moduleDataMergeOperationOnContainerTest() throws Exception {
        checkPatchContext(parse(mountPrefix(), "instance-identifier-patch-module:patch-cont", """
            <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                <patch-id>Test merge operation</patch-id>
                <comment>This is test patch for merge operation on container</comment>
                <edit>
                    <edit-id>edit1</edit-id>
                    <operation>create</operation>
                    <target>/</target>
                    <value>
                        <patch-cont xmlns="instance:identifier:patch:module">
                            <my-list1>
                                <name>my-list1 - A</name>
                                <my-leaf11>I am leaf11-0</my-leaf11>
                                <my-leaf12>I am leaf12-1</my-leaf12>
                            </my-list1>
                            <my-list1>
                                <name>my-list1 - B</name>
                                <my-leaf11>I am leaf11-0</my-leaf11>
                                <my-leaf12>I am leaf12-1</my-leaf12>
                            </my-list1>
                        </patch-cont>
                    </value>
                </edit>
                <edit>
                    <edit-id>edit2</edit-id>
                    <operation>merge</operation>
                    <target>/</target>
                    <value>
                        <patch-cont xmlns="instance:identifier:patch:module">
                            <my-list1>
                                <name>my-list1 - Merged</name>
                                <my-leaf11>I am leaf11-0</my-leaf11>
                                <my-leaf12>I am leaf12-1</my-leaf12>
                            </my-list1>
                        </patch-cont>
                    </value>
                </edit>
            </yang-patch>"""));
    }

    /**
     * Test of Yang Patch on the top-level container with empty URI for data root.
     */
    @Test
    final void modulePatchTargetTopLevelContainerWithEmptyURITest() throws Exception {
        checkPatchContext(parse(mountPrefix(), "", """
            <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                <patch-id>test-patch</patch-id>
                <comment>Test patch applied to the top-level container with empty URI</comment>
                <edit>
                    <edit-id>edit1</edit-id>
                    <operation>replace</operation>
                    <target>/instance-identifier-patch-module:patch-cont</target>
                    <value>
                        <patch-cont xmlns="instance:identifier:patch:module">
                            <my-list1>
                                <name>my-leaf10</name>
                            </my-list1>
                        </patch-cont>
                    </value>
                </edit>
            </yang-patch>"""));
    }

    /**
     * Test of YANG Patch on the top-level container with the full path in the URI and "/" in 'target'.
     */
    @Test
    final void modulePatchTargetTopLevelContainerWithFullPathURITest() throws Exception {
        final var returnValue = parse(mountPrefix(), "instance-identifier-patch-module:patch-cont", """
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
        assertEquals(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(PATCH_CONT_QNAME))
            .withChild(ImmutableNodes.newSystemMapBuilder()
                .withNodeIdentifier(new NodeIdentifier(MY_LIST1_QNAME))
                .withChild(ImmutableNodes.newMapEntryBuilder()
                    .withNodeIdentifier(NodeIdentifierWithPredicates.of(MY_LIST1_QNAME, LEAF_NAME_QNAME, "my-leaf-set"))
                    .withChild(ImmutableNodes.leafNode(LEAF_NAME_QNAME, "my-leaf-set"))
                    .withChild(ImmutableNodes.leafNode(MY_LEAF11_QNAME, "leaf-a"))
                    .withChild(ImmutableNodes.leafNode(MY_LEAF12_QNAME, "leaf-b"))
                    .build())
                .build())
            .build(), returnValue.entities().get(0).getNode());
    }

    /**
     * Test of YANG Patch on the second-level list with the full path in the URI and "/" in 'target'.
     */
    @Test
    final void modulePatchTargetSecondLevelListWithFullPathURITest() throws Exception {
        final var returnValue = parse(mountPrefix(), "instance-identifier-patch-module:patch-cont/my-list1=my-leaf-set",
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
        assertEquals(ImmutableNodes.newSystemMapBuilder()
            .withNodeIdentifier(new NodeIdentifier(MY_LIST1_QNAME))
            .withChild(ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(MY_LIST1_QNAME, LEAF_NAME_QNAME, "my-leaf-set"))
                .withChild(ImmutableNodes.leafNode(LEAF_NAME_QNAME, "my-leaf-set"))
                .withChild(ImmutableNodes.leafNode(MY_LEAF11_QNAME, "leaf-a"))
                .withChild(ImmutableNodes.leafNode(MY_LEAF12_QNAME, "leaf-b"))
                .build())
            .build(), returnValue.entities().get(0).getNode());
    }

    /**
     * Test of Yang Patch on the top augmented element.
     */
    @Test
    final void moduleTargetTopLevelAugmentedContainerTest() throws Exception {
        final var returnValue = parse(mountPrefix(), "", """
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
        assertEquals(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(CONT_AUG_QNAME))
            .withChild(ImmutableNodes.leafNode(LEAF_AUG_QNAME, "data"))
            .build(), returnValue.entities().get(0).getNode());
    }

    /**
     * Test of YANG Patch on the top system map node element.
     */
    @Test
    final void moduleTargetMapNodeTest() throws Exception {
        final var returnValue = parse(mountPrefix(), "", """
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
        assertEquals(ImmutableNodes.newSystemMapBuilder()
            .withNodeIdentifier(new NodeIdentifier(MAP_CONT_QNAME))
            .withChild(ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(MAP_CONT_QNAME, KEY_LEAF_QNAME, "key"))
                .withChild(ImmutableNodes.leafNode(KEY_LEAF_QNAME, "key"))
                .withChild(ImmutableNodes.leafNode(DATA_LEAF_QNAME, "data"))
                .build())
            .build(), returnValue.entities().get(0).getNode());
    }

    /**
     * Test of YANG Patch on the leaf set node element.
     */
    @Test
    final void modulePatchTargetLeafSetNodeTest() throws Exception {
        final var returnValue = parse(mountPrefix(), "", """
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
        assertEquals(ImmutableNodes.newSystemLeafSetBuilder()
            .withNodeIdentifier(new NodeIdentifier(LEAF_SET_QNAME))
            .withChildValue("data1")
            .build(), returnValue.entities().get(0).getNode());
    }

    /**
     * Test of Yang Patch on the top unkeyed list element.
     */
    @Test
    final void moduleTargetUnkeyedListNodeTest() throws Exception {
        final var returnValue = parse(mountPrefix(), "", """
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
        assertEquals(ImmutableNodes.newUnkeyedListBuilder()
            .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
            .withChild(ImmutableNodes.newUnkeyedListEntryBuilder()
                .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
                .withChild(ImmutableNodes.leafNode(LIST_LEAF1_QNAME, "data1"))
                .withChild(ImmutableNodes.leafNode(LIST_LEAF2_QNAME, "data2"))
                .build())
            .build(), returnValue.entities().get(0).getNode());
    }

    /**
     * Test of Yang Patch on the top case node element.
     */
    @Test
    final void moduleTargetCaseNodeTest() throws Exception {
        final var returnValue = parse(mountPrefix(), "", """
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
        assertEquals(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(CHOICE_CONT_QNAME))
            .withChild(ImmutableNodes.leafNode(CASE_LEAF1_QNAME, "data"))
            .build(), returnValue.entities().get(0).getNode());
    }

    /**
     * Test reading simple leaf value.
     */
    @Test
    final void modulePatchSimpleLeafValueTest() throws Exception {
        final var returnValue = parse(mountPrefix(), "instance-identifier-patch-module:patch-cont/my-list1=leaf1", """
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
        assertEquals(ImmutableNodes.leafNode(LEAF_NAME_QNAME, "my-leaf20"), returnValue.entities().get(0).getNode());
    }
}
