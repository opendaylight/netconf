/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.streams.listeners;

import java.util.List;
import java.util.Set;
import org.opendaylight.netconf.sal.restconf.impl.RestCodec;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.model.api.AnyXmlSchemaNode;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchema;
import org.opendaylight.yangtools.yang.model.api.ChoiceCaseNode;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser of data node to {@link DataSchemaNode} by list of {@link PathArgument}
 */
public class ParserOfSchemaNode {

    private static final Logger LOG = LoggerFactory.getLogger(RestCodec.class);

    private ParserOfSchemaNode() {
        throw new UnsupportedOperationException("Util class.");
    }

    /**
     * Parsing {@link DataSchemaNode} from list of {@link PathArgument} of data
     * by {@link SchemaContext}
     *
     * @param list
     *            - list of {@link PathArgument}
     * @param schemaContext
     *            - {@link SchemaContext}
     * @return specific {@link DataSchemaNode} - should be
     *         {@link LeafSchemaNode}
     */
    public static DataSchemaNode parseFrom(final List<PathArgument> list, final SchemaContext schemaContext) {
        return parseFrom(list.subList(1, list.size()), schemaContext, null);
    }

    private static DataSchemaNode parseFrom(final List<PathArgument> list, final SchemaContext schemaContext,
            final DataSchemaNode schemaNode) {
        while (!list.isEmpty()) {
            final PathArgument pathArgument = list.get(0);
            QName nodeQName = null;
            if (pathArgument instanceof AugmentationIdentifier) {
                nodeQName = ((AugmentationIdentifier) pathArgument).getPossibleChildNames().iterator().next();
            } else {
                nodeQName = pathArgument.getNodeType();
            }
            final Module module = schemaContext.findModuleByNamespaceAndRevision(nodeQName.getNamespace(),nodeQName.getRevision());
            DataSchemaNode schameNodeOfModule = findSchemaOfNodeInModule(module, nodeQName);
            if ((schameNodeOfModule == null) && (schemaNode != null)) {
                schameNodeOfModule = findSchemaNodeOfSchemaNode(schemaNode, nodeQName);
            }
            if (schameNodeOfModule == null) {
                final String msg = "Schema node of " + nodeQName + "doesn't exist in " + schemaNode.getQName();
                LOG.error(msg);
                throw new RestconfDocumentedException(msg);
            }
            return parseFrom(list.subList(1, list.size()), schemaContext, schameNodeOfModule);
        }
        return schemaNode;
    }

    private static DataSchemaNode findSchemaNodeOfSchemaNode(final DataSchemaNode schemaNode, final QName nodeQName) {
        if(schemaNode instanceof AnyXmlSchemaNode){
            final String msg = "Not yet fully implemented in yangtools.";
            LOG.error(msg);
            throw new UnsupportedOperationException(msg);
        } else if (schemaNode instanceof ChoiceSchemaNode) {
            for (final ChoiceCaseNode choiceCaseNode : ((ChoiceSchemaNode) schemaNode).getCases()) {
                for (final DataSchemaNode choiceLeafSchemaNode : choiceCaseNode.getChildNodes()) {
                    if (choiceLeafSchemaNode.getQName().equals(nodeQName)) {
                        return choiceLeafSchemaNode;
                    }
                }
            }

        } else if (schemaNode instanceof ContainerSchemaNode) {
            final ContainerSchemaNode containerSchemaNode = (ContainerSchemaNode) schemaNode;
            return containerSchemaNode.getDataChildByName(nodeQName);
        } else if (schemaNode instanceof ListSchemaNode) {
            return ((ListSchemaNode) schemaNode).getDataChildByName(nodeQName);
        } else if ((schemaNode instanceof LeafSchemaNode) || (schemaNode instanceof LeafListSchemaNode)) {
            return schemaNode;
        }
        return null;
    }

    private static DataSchemaNode findSchemaOfNodeInModule(final Module module, final QName qnameOfNode) {
        //find in data child
        DataSchemaNode schemaNode = module.getDataChildByName(qnameOfNode);
        if (schemaNode == null) {
            // find in augmentations
            schemaNode = findSchemaNode(module.getAugmentations(), AugmentationSchema.class, qnameOfNode);
            if (schemaNode != null) {
                return schemaNode;
            }
            // find in groupings
            schemaNode = findSchemaNode(module.getGroupings(), GroupingDefinition.class, qnameOfNode);
            if (schemaNode != null) {
                return schemaNode;
            }
            // find in notifications
            schemaNode = findSchemaNode(module.getNotifications(), NotificationDefinition.class, qnameOfNode);
        }
        return schemaNode;
    }

    private static <T extends DataNodeContainer> DataSchemaNode findSchemaNode(final Set<T> childs,
            final Class<T> typeOfChild, final QName qnameOfNode) {
        for (final T child : childs) {
            final DataSchemaNode dataChildByName = child.getDataChildByName(qnameOfNode);
            if (dataChildByName != null) {
                return dataChildByName;
            }
        }
        return null;
    }

}
