/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.opendaylight.yangtools.yang.model.api.AnyXmlSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ChoiceCaseNode;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public class RestSchemaControllerUtil {

    public final static String MOUNT = "yang-ext:mount";

    private RestSchemaControllerUtil() {
        throw new UnsupportedOperationException("Util class");
    }

    public static List<DataSchemaNode> findInstanceDataChildrenByName(final DataNodeContainer container,
            final String name) {
        Preconditions.<DataNodeContainer> checkNotNull(container);
        Preconditions.<String> checkNotNull(name);

        final List<DataSchemaNode> instantiatedDataNodeContainers = new ArrayList<DataSchemaNode>();
        collectInstanceDataNodeContainers(instantiatedDataNodeContainers, container, name);
        return instantiatedDataNodeContainers;
    }

    private static final Function<ChoiceSchemaNode, Set<ChoiceCaseNode>> CHOICE_FUNCTION = new Function<ChoiceSchemaNode, Set<ChoiceCaseNode>>() {
        @Override
        public Set<ChoiceCaseNode> apply(final ChoiceSchemaNode node) {
            return node.getCases();
        }
    };

    private static void collectInstanceDataNodeContainers(final List<DataSchemaNode> potentialSchemaNodes,
            final DataNodeContainer container, final String name) {

        final Predicate<DataSchemaNode> filter = new Predicate<DataSchemaNode>() {
            @Override
            public boolean apply(final DataSchemaNode node) {
                return Objects.equal(node.getQName().getLocalName(), name);
            }
        };

        final Iterable<DataSchemaNode> nodes = Iterables.filter(container.getChildNodes(), filter);

        for (final DataSchemaNode potentialNode : nodes) {
            if (isInstantiatedDataSchema(potentialNode)) {
                potentialSchemaNodes.add(potentialNode);
            }
        }

        final Iterable<ChoiceSchemaNode> choiceNodes = Iterables.filter(container.getChildNodes(),
                ChoiceSchemaNode.class);
        final Iterable<Set<ChoiceCaseNode>> map = Iterables.transform(choiceNodes, CHOICE_FUNCTION);

        final Iterable<ChoiceCaseNode> allCases = Iterables.<ChoiceCaseNode> concat(map);
        for (final ChoiceCaseNode caze : allCases) {
            collectInstanceDataNodeContainers(potentialSchemaNodes, caze, name);
        }
    }

    public static boolean isInstantiatedDataSchema(final DataSchemaNode node) {
        return (node instanceof LeafSchemaNode) || (node instanceof LeafListSchemaNode)
                || (node instanceof ContainerSchemaNode) || (node instanceof ListSchemaNode)
                || (node instanceof AnyXmlSchemaNode);
    }
}
