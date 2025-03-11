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
import org.opendaylight.restconf.server.api.JsonPatchBody;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

class JsonPatchBodyTest extends AbstractPatchBodyTest {
    JsonPatchBodyTest() {
        super(JsonPatchBody::new);
    }

    @Test
    final void modulePatchDataTest() throws Exception {
        checkPatchContext(parse(mountPrefix(), "instance-identifier-patch-module:patch-cont/my-list1=leaf1", """
            {
              "ietf-yang-patch:yang-patch" : {
                "patch-id" : "test-patch",
                "comment" : "this is test patch",
                "edit" : [
                  {
                    "edit-id": "edit1",
                    "operation": "replace",
                    "target": "/my-list2=my-leaf20",
                    "value": {
                      "my-list2": {
                        "name": "my-leaf20",
                        "my-leaf21": "I am leaf21-0",
                        "my-leaf22": "I am leaf22-0"
                       }
                    }
                  },
                  {
                    "edit-id": "edit2",
                    "operation": "replace",
                    "target": "/my-list2=my-leaf20",
                    "value": {
                      "my-list2": {
                        "name": "my-leaf20",
                        "my-leaf21": "I am leaf21-1",
                        "my-leaf22": "I am leaf22-1",
                        "my-leaf-list": ["listelement"]
                      }
                    }
                  }
                ]
              }
            }"""));
    }

    /**
     * Test of successful Patch consisting of create and delete Patch operations.
     */
    @Test
    final void modulePatchCreateAndDeleteTest() throws Exception {
        checkPatchContext(parse(mountPrefix(), "instance-identifier-patch-module:patch-cont/my-list1=leaf1", """
            {
              "ietf-yang-patch:yang-patch" : {
                "patch-id" : "test-patch",
                "comment" : "this is test patch",
                "edit" : [
                  {
                    "edit-id": "edit1",
                    "value": {
                      "my-list2": [
                        {
                          "name": "my-leaf20",
                          "my-leaf21": "I am leaf20"
                        },
                        {
                          "name": "my-leaf21",
                          "my-leaf21": "I am leaf21-1",
                          "my-leaf22": "I am leaf21-2"
                        }
                      ]
                    },
                    "target": "/my-list2=my-leaf20",
                    "operation": "create"
                  },
                  {
                    "edit-id": "edit2",
                    "operation": "delete",
                    "target": "/my-list2=my-leaf20"
                  }
                ]
              }
            }"""));
    }

    /**
     * Test trying to use Patch create operation which requires value without value. Test should fail with
     * {@link RequestException} with error code 400.
     */
    @Test
    final void modulePatchValueMissingNegativeTest() {
        final var ex = assertThrows(RequestException.class,
            () -> parse(mountPrefix(), "instance-identifier-patch-module:patch-cont/my-list1=leaf1", """
                {
                  "ietf-yang-patch:yang-patch" : {
                    "patch-id" : "test-patch",
                    "comment" : "this is test patch",
                    "edit" : [
                      {
                        "edit-id": "edit1",
                        "target": "/my-list2=my-leaf20",
                        "operation": "create"
                      }
                    ]
                  }
                }"""));
        final var requestError = ex.errors().get(0);
        assertEquals(ErrorTag.INVALID_VALUE, requestError.tag());
        assertEquals(ErrorType.APPLICATION, requestError.type());
        assertEquals("Create operation requires 'value' element", requestError.message().elementBody());
    }

    /**
     * Test trying to use value with Patch delete operation which does not support value. Test should fail with
     * {@link RequestException} with error code 400.
     */
    @Test
    final void modulePatchValueNotSupportedNegativeTest() {
        final var ex = assertThrows(RequestException.class,
            () -> parse(mountPrefix(), "instance-identifier-patch-module:patch-cont/my-list1=leaf1", """
                {
                  "ietf-yang-patch:yang-patch" : {
                    "patch-id" : "test-patch",
                    "comment" : "this is test patch",
                    "edit" : [
                      {
                        "edit-id": "edit2",
                        "operation": "delete",
                        "target": "/my-list2=my-leaf20",
                        "value": {
                          "my-list2": [
                            {
                              "name": "my-leaf20"
                            }
                          ]
                        }
                      }
                    ]
                  }
                }"""));
        final var requestError = ex.errors().get(0);
        assertEquals(ErrorTag.INVALID_VALUE, requestError.tag());
        assertEquals(ErrorType.APPLICATION, requestError.type());
        assertEquals("Delete operation can not have 'value' element", requestError.message().elementBody());
    }

    /**
     * Test using Patch when target is completely specified in request URI and thus target leaf contains only '/' sign.
     */
    @Test
    final void modulePatchCompleteTargetInURITest() throws Exception {
        checkPatchContext(parse(mountPrefix(), "instance-identifier-patch-module:patch-cont", """
            {
              "ietf-yang-patch:yang-patch" : {
                "patch-id" : "test-patch",
                "comment" : "Test to create and replace data in container directly using / sign as a target",
                "edit" : [
                  {
                    "edit-id": "edit1",
                    "operation": "create",
                    "target": "/",
                    "value": {
                      "patch-cont": {
                        "my-list1": [
                          {
                            "name": "my-list1 - A",
                            "my-leaf11": "I am leaf11-0",
                            "my-leaf12": "I am leaf12-1"
                          },
                          {
                            "name": "my-list1 - B",
                            "my-leaf11": "I am leaf11-0",
                            "my-leaf12": "I am leaf12-1"
                          }
                        ]
                      }
                    }
                  },
                  {
                    "edit-id": "edit2",
                    "operation": "replace",
                    "target": "/",
                    "value": {
                      "patch-cont": {
                        "my-list1": {
                          "name": "my-list1 - Replacing",
                          "my-leaf11": "I am leaf11-0",
                          "my-leaf12": "I am leaf12-1"
                        }
                      }
                    }
                  }
                ]
              }
            }"""));
    }

    /**
     * Test of YANG Patch merge operation on list. Test consists of two edit operations - replace and merge.
     */
    @Test
    final void modulePatchMergeOperationOnListTest() throws Exception {
        checkPatchContext(parse(mountPrefix(), "instance-identifier-patch-module:patch-cont/my-list1=leaf1", """
            {
              "ietf-yang-patch:yang-patch" : {
                "patch-id" : "Test merge operation",
                "comment" : "This is test patch for merge operation on list",
                "edit" : [
                  {
                    "edit-id": "edit1",
                    "operation": "replace",
                    "target": "/my-list2=my-leaf20",
                    "value": {
                      "my-list2": {
                        "name": "my-leaf20",
                        "my-leaf21": "I am leaf21-0",
                        "my-leaf22": "I am leaf22-0"
                      }
                    }
                  },
                  {
                    "edit-id": "edit2",
                    "operation": "merge",
                    "target": "/my-list2=my-leaf21",
                    "value": {
                      "my-list2": {
                        "name": "my-leaf21",
                        "my-leaf21": "I am leaf21-1",
                        "my-leaf22": "I am leaf22-1"
                      }
                    }
                  }
                ]
              }
            }"""));
    }

    /**
     * Test of YANG Patch merge operation on container. Test consists of two edit operations - create and merge.
     */
    @Test
    final void modulePatchMergeOperationOnContainerTest() throws Exception {
        checkPatchContext(parse(mountPrefix(), "instance-identifier-patch-module:patch-cont", """
            {
              "ietf-yang-patch:yang-patch" : {
                "patch-id" : "Test merge operation",
                "comment" : "This is test patch for merge operation on container",
                "edit" : [
                  {
                    "edit-id": "edit1",
                    "operation": "create",
                    "target": "/",
                    "value": {
                      "patch-cont": {
                        "my-list1": [
                          {
                            "name": "my-list1 - A",
                            "my-leaf11": "I am leaf11-0",
                            "my-leaf12": "I am leaf12-1"
                          },
                          {
                            "name": "my-list1 - B",
                            "my-leaf11": "I am leaf11-0",
                            "my-leaf12": "I am leaf12-1"
                          }
                        ]
                      }
                    }
                  },
                  {
                    "edit-id": "edit2",
                    "operation": "merge",
                    "target": "/",
                    "value": {
                      "patch-cont": {
                        "my-list1": {
                          "name": "my-list1 - Merged",
                          "my-leaf11": "I am leaf11-0",
                          "my-leaf12": "I am leaf12-1"
                        }
                      }
                    }
                  }
                ]
              }
            }"""));
    }

    /**
     * Test reading simple leaf value.
     */
    @Test
    final void modulePatchSimpleLeafValueTest() throws Exception {
        final var returnValue = parse(mountPrefix(), "instance-identifier-patch-module:patch-cont/my-list1=leaf1", """
            {
              "ietf-yang-patch:yang-patch" : {
                "patch-id" : "test-patch",
                "comment" : "this is test patch for simple leaf value",
                "edit" : [
                  {
                    "edit-id": "edit1",
                    "operation": "replace",
                    "target": "/my-list2=my-leaf20/name",
                    "value": {
                      "name": "my-leaf20"
                    }
                  }
                ]
              }
            }""");
        checkPatchContext(returnValue);
        assertEquals(ImmutableNodes.leafNode(LEAF_NAME_QNAME, "my-leaf20"), returnValue.entities().get(0).getNode());
    }

    /**
     * Test of YANG Patch on the top-level container with empty URI for data root.
     */
    @Test
    final void modulePatchTargetTopLevelContainerWithEmptyURITest() throws Exception {
        checkPatchContext(parse(mountPrefix(), "", """
            {
              "ietf-yang-patch:yang-patch" : {
                "patch-id" : "test-patch",
                "comment" : "Test patch applied to the top-level container with empty URI",
                "edit" : [
                  {
                    "edit-id": "edit1",
                    "operation": "replace",
                    "target": "/instance-identifier-patch-module:patch-cont",
                    "value": {
                      "patch-cont": {
                        "my-list1": [
                          {
                            "name": "my-leaf10"
                          }
                        ]
                      }
                    }
                  }
                ]
              }
            }"""));
    }

    /**
     * Test of YANG Patch on the top-level container with the full path in the URI and "/" in 'target'.
     */
    @Test
    final void modulePatchTargetTopLevelContainerWithFullPathURITest() throws Exception {
        final var returnValue = parse(mountPrefix(), "instance-identifier-patch-module:patch-cont", """
            {
              "ietf-yang-patch:yang-patch": {
                "patch-id": "test-patch",
                "comment": "Test patch applied to the top-level container with '/' in target",
                "edit": [
                  {
                    "edit-id": "edit1",
                    "operation": "replace",
                    "target": "/",
                    "value": {
                      "patch-cont": {
                        "my-list1": [
                          {
                            "name": "my-leaf-set",
                            "my-leaf11": "leaf-a",
                            "my-leaf12": "leaf-b"
                          }
                        ]
                      }
                    }
                  }
                ]
              }
            }""");
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
            {
              "ietf-yang-patch:yang-patch": {
                "patch-id": "test-patch",
                "comment": "Test patch applied to the second-level list with '/' in target",
                "edit": [
                  {
                    "edit-id": "edit1",
                    "operation": "replace",
                    "target": "/",
                    "value": {
                      "my-list1": [
                        {
                          "name": "my-leaf-set",
                          "my-leaf11": "leaf-a",
                          "my-leaf12": "leaf-b"
                        }
                      ]
                    }
                  }
                ]
              }
            }""");
        checkPatchContext(returnValue);
        assertEquals(ImmutableNodes.newSystemMapBuilder()
            .withNodeIdentifier(new NodeIdentifier(MY_LIST1_QNAME))
            .withChild(ImmutableNodes.newMapEntryBuilder()
                    .withNodeIdentifier(NodeIdentifierWithPredicates.of(
                            MY_LIST1_QNAME, LEAF_NAME_QNAME, "my-leaf-set"))
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
    final void modulePatchTargetTopLevelAugmentedContainerTest() throws Exception {
        final var returnValue = parse(mountPrefix(), "", """
            {
                "ietf-yang-patch:yang-patch": {
                    "patch-id": "test-patch",
                    "comment": "comment",
                    "edit": [
                        {
                            "edit-id": "edit1",
                            "operation": "replace",
                            "target": "/test-m:container-root/test-m:container-lvl1/test-m-aug:container-aug",
                            "value": {
                                "container-aug": {
                                    "leaf-aug": "data"
                                }
                            }
                        }
                    ]
                }
            }""");
        checkPatchContext(returnValue);
        assertEquals(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(CONT_AUG_QNAME))
            .withChild(ImmutableNodes.leafNode(LEAF_AUG_QNAME, "data"))
            .build(), returnValue.entities().get(0).getNode());
    }

    /**
     * Test of YANG Patch on the system map node element.
     */
    @Test
    final void modulePatchTargetMapNodeTest() throws Exception {
        final var returnValue = parse(mountPrefix(), "", """
            {
                "ietf-yang-patch:yang-patch": {
                    "patch-id": "map-patch",
                    "comment": "comment",
                    "edit": [
                        {
                            "edit-id": "edit1",
                            "operation": "replace",
                            "target": "/map-model:cont-root/map-model:cont1/map-model:my-map=key",
                            "value": {
                                "my-map": {
                                    "key-leaf": "key",
                                    "data-leaf": "data"
                                }
                            }
                        }
                    ]
                }
            }""");
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
     * Test of Yang Patch on the leaf set node element.
     */
    @Test
    final void modulePatchTargetLeafSetNodeTest() throws Exception {
        final var returnValue = parse(mountPrefix(), "", """
            {
                "ietf-yang-patch:yang-patch": {
                    "patch-id": "set-patch",
                    "comment": "comment",
                    "edit": [
                        {
                            "edit-id": "edit1",
                            "operation": "replace",
                            "target": "/set-model:cont-root/set-model:cont1/set-model:my-set=data1",
                            "value": {
                                "my-set": [ "data1" ]
                            }
                        }
                    ]
                }
            }""");
        checkPatchContext(returnValue);
        assertEquals(ImmutableNodes.newSystemLeafSetBuilder()
            .withNodeIdentifier(new NodeIdentifier(LEAF_SET_QNAME))
            .withChildValue("data1")
            .build(), returnValue.entities().get(0).getNode());
    }

    /**
     * Test of Yang Patch on the unkeyed list node element.
     */
    @Test
    final void modulePatchTargetUnkeyedListNodeTest() throws Exception {
        final var returnValue = parse(mountPrefix(), "", """
            {
                "ietf-yang-patch:yang-patch": {
                    "patch-id": "list-patch",
                    "comment": "comment",
                    "edit": [
                        {
                            "edit-id": "edit1",
                            "operation": "replace",
                            "target": "/list-model:cont-root/list-model:cont1/list-model:unkeyed-list",
                            "value": {
                                "unkeyed-list": {
                                    "leaf1": "data1",
                                    "leaf2": "data2"
                                }
                            }
                        }
                    ]
                }
            }""");
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
     * Test of Yang Patch on the case node element.
     */
    @Test
    final void modulePatchTargetCaseNodeTest() throws Exception {
        final var returnValue = parse(mountPrefix(), "", """
            {
                "ietf-yang-patch:yang-patch": {
                    "patch-id": "choice-patch",
                    "comment": "comment",
                    "edit": [
                        {
                            "edit-id": "edit1",
                            "operation": "replace",
                            "target": "/choice-model:cont-root/choice-model:cont1/choice-model:case-cont1",
                            "value": {
                                "case-cont1": {
                                    "case-leaf1": "data"
                                }
                            }
                        }
                    ]
                }
            }""");
        checkPatchContext(returnValue);
        assertEquals(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(CHOICE_CONT_QNAME))
            .withChild(ImmutableNodes.leafNode(CASE_LEAF1_QNAME, "data"))
            .build(), returnValue.entities().get(0).getNode());
    }
}
