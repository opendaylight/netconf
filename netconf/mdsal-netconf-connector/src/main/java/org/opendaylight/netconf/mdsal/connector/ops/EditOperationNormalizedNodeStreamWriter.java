/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops;

import java.util.ArrayList;
import java.util.Map;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.EditConfigInput;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.ModifyAction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamAttributeWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.NormalizedNodeContainerBuilder;

final class EditOperationNormalizedNodeStreamWriter extends ImmutableNormalizedNodeStreamWriter
        implements NormalizedNodeStreamAttributeWriter {
    private static final QName OPERATION_ATTRIBUTE = QName.create(EditConfigInput.QNAME.getNamespace(),
            XmlNetconfConstants.OPERATION_ATTR_KEY);

    private final DataTreeChangeTracker dataTreeChangeTracker;

    EditOperationNormalizedNodeStreamWriter(final NormalizedNodeResult result,
            final DataTreeChangeTracker dataTreeChangeTracker) {
        super(result);
        this.dataTreeChangeTracker = dataTreeChangeTracker;
    }

    @Override
    public void leafNode(final NodeIdentifier name, final Object value, final Map<QName, String> attributes) {
        super.leafNode(name, value);

        final String operation = attributes.get(OPERATION_ATTRIBUTE);
        if (operation == null) {
            return;
        }

        final ModifyAction action = ModifyAction.fromXmlValue(operation);
        if (dataTreeChangeTracker.getDeleteOperationTracker() == 0
                && dataTreeChangeTracker.getRemoveOperationTracker() == 0) {
            if (!action.equals(dataTreeChangeTracker.peekAction())) {
                final LeafNode<Object> node = ImmutableNodes.leafNode(name, value);
                dataTreeChangeTracker.pushPath(name);
                dataTreeChangeTracker.addDataTreeChange(new DataTreeChangeTracker.DataTreeChange(node, action,
                        new ArrayList<>(dataTreeChangeTracker.getCurrentPath())));
                getCurrent().removeChild(dataTreeChangeTracker.popPath());
            }
        }
    }

    @Override
    public void startLeafSet(final NodeIdentifier name, final int childSizeHint) {
        super.startLeafSet(name, childSizeHint);
        dataTreeChangeTracker.pushPath(name);
    }

    @Override
    public void startOrderedLeafSet(final NodeIdentifier name, final int childSizeHint) {
        super.startOrderedLeafSet(name, childSizeHint);
        dataTreeChangeTracker.pushPath(name);
    }

    @Override
    public void leafSetEntryNode(final QName name, final Object value, final Map<QName, String> attributes) {
        super.leafSetEntryNode(name, value);
        final String operation = attributes.get(OPERATION_ATTRIBUTE);
        if (operation == null) {
            return;
        }

        ModifyAction action = ModifyAction.fromXmlValue(operation);
        if (dataTreeChangeTracker.getDeleteOperationTracker() == 0
                && dataTreeChangeTracker.getRemoveOperationTracker() == 0) {
            if (!action.equals(dataTreeChangeTracker.peekAction())) {
                final LeafSetEntryNode<?> node = Builders.leafSetEntryBuilder().withNodeIdentifier(
                        new NodeWithValue(name, value)).withValue(value).build();
                dataTreeChangeTracker.pushPath(node.getIdentifier());
                dataTreeChangeTracker.addDataTreeChange(new DataTreeChangeTracker.DataTreeChange(node, action,
                        new ArrayList<>(dataTreeChangeTracker.getCurrentPath())));
                getCurrent().removeChild(dataTreeChangeTracker.popPath());
            }
        }
    }

    @Override
    public void startContainerNode(final NodeIdentifier name, final int childSizeHint,
            final Map<QName, String> attributes) {
        super.startContainerNode(name, childSizeHint);
        trackDataContainerNode(name, attributes);
    }

    @Override
    public void startYangModeledAnyXmlNode(final NodeIdentifier name, final int childSizeHint,
            final Map<QName, String> attributes) {
        super.startYangModeledAnyXmlNode(name, childSizeHint);
        trackDataContainerNode(name, attributes);
    }

    @Override
    public void startUnkeyedList(final NodeIdentifier name, final int childSizeHint) {
        super.startUnkeyedList(name, childSizeHint);
        dataTreeChangeTracker.pushPath(name);
    }

    @Override
    public void startUnkeyedListItem(final NodeIdentifier name, final int childSizeHint,
            final Map<QName, String> attributes) {
        super.startUnkeyedListItem(name, childSizeHint);
        trackDataContainerNode(name, attributes);
    }

    @Override
    public void startMapNode(final NodeIdentifier name, final int childSizeHint) {
        super.startMapNode(name, childSizeHint);
        dataTreeChangeTracker.pushPath(name);
    }

    @Override
    public void startOrderedMapNode(final NodeIdentifier name, final int childSizeHint) {
        super.startOrderedMapNode(name, childSizeHint);
        dataTreeChangeTracker.pushPath(name);
    }

    @Override
    public void startMapEntryNode(final NodeIdentifierWithPredicates identifier, final int childSizeHint,
            final Map<QName, String> attributes)  {
        super.startMapEntryNode(identifier, childSizeHint);
        trackDataContainerNode(identifier, attributes);
    }

    @Override
    public void startAugmentationNode(final AugmentationIdentifier identifier) {
        super.startAugmentationNode(identifier);
        trackMixinNode(identifier);
    }

    @Override
    public void startChoiceNode(final NodeIdentifier name, final int childSizeHint) {
        super.startChoiceNode(name, childSizeHint);
        trackMixinNode(name);
    }

    // for augments and choices
    private void trackMixinNode(final PathArgument identifier) {
        dataTreeChangeTracker.pushPath(identifier);
        dataTreeChangeTracker.pushAction(dataTreeChangeTracker.peekAction() != null
                ? dataTreeChangeTracker.peekAction() : dataTreeChangeTracker.getDefaultAction());
    }

    // for containers, (unkeyed) list entries and yang-modeled-anyxmls
    private void trackDataContainerNode(final PathArgument identifier, final Map<QName, String> attributes) {
        dataTreeChangeTracker.pushPath(identifier);
        final String operation = attributes.get(OPERATION_ATTRIBUTE);
        if (operation != null) {
            dataTreeChangeTracker.pushAction(ModifyAction.fromXmlValue(operation));
        } else {
            dataTreeChangeTracker.pushAction(dataTreeChangeTracker.peekAction() != null
                    ? dataTreeChangeTracker.peekAction() : dataTreeChangeTracker.getDefaultAction());
        }
    }

    @Override
    @SuppressWarnings({"rawtypes","unchecked"})
    public void endNode() {
        final NormalizedNodeContainerBuilder finishedBuilder = getBuilders().peek();
        final NormalizedNode<PathArgument, ?> product = finishedBuilder.build();
        super.endNode();

        // for augments, choices, containers, (unkeyed) list entries and yang-modeled-anyxmls
        if (finishedBuilder instanceof DataContainerNodeBuilder) {
            final ModifyAction currentAction = dataTreeChangeTracker.popAction();

            //if we know that we are going to delete a parent node just complete the entire subtree
            if (dataTreeChangeTracker.getDeleteOperationTracker() > 0
                    || dataTreeChangeTracker.getRemoveOperationTracker() > 0) {
                dataTreeChangeTracker.popPath();
            } else {
                //if parent and current actions don't match create a DataTreeChange and add it to the change list
                //don't add a new child to the parent node
                if (!currentAction.equals(dataTreeChangeTracker.peekAction())) {
                    dataTreeChangeTracker.addDataTreeChange(new DataTreeChangeTracker.DataTreeChange(product,
                            currentAction, new ArrayList<>(dataTreeChangeTracker.getCurrentPath())));
                    if (getCurrent() instanceof NormalizedNodeResultBuilder) {
                        dataTreeChangeTracker.popPath();
                        return;
                    }
                    getCurrent().removeChild(dataTreeChangeTracker.popPath());
                } else {
                    dataTreeChangeTracker.popPath();
                    return;
                }
            }
        }

        // for (ordered) leaf-lists, (ordered) lists and unkeyed lists
        if (finishedBuilder instanceof CollectionNodeBuilder) {
            dataTreeChangeTracker.popPath();
        }
    }
}
