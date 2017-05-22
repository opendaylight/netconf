/*
 * Copyright (c) 2017 Frinx s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector.ops.get;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.ChoiceCaseNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;

public class FilterTree {
    private final QName name;
    private final FilterTreeType filterTreeType;
    private final DataSchemaNode schemaNode;
    private final Map<QName, FilterTree> children;

    FilterTree(final QName name, final FilterTreeType filterTreeType, final DataSchemaNode schemaNode) {
        this.name = name;
        this.filterTreeType = filterTreeType;
        this.schemaNode = schemaNode;
        this.children = new HashMap<>();
    }

    FilterTree addChild(final DataSchemaNode data) {
        final FilterTreeType filterTreeType;
        if (data instanceof ChoiceCaseNode) {
            filterTreeType = FilterTreeType.CHOICE_CASE;
        } else if (data instanceof ListSchemaNode) {
            filterTreeType = FilterTreeType.LIST;
        } else {
            filterTreeType = FilterTreeType.OTHER;
        }
        final QName name = data.getQName();
        FilterTree childTree = children.get(name);
        if (childTree == null) {
            childTree = new FilterTree(name, filterTreeType, data);
        }
        children.put(name, childTree);
        return childTree;
    }

    Collection<FilterTree> getChildren() {
        return children.values();
    }

    QName getName() {
        return name;
    }

    FilterTreeType getFilterTreeType() {
        return filterTreeType;
    }

    DataSchemaNode getSchemaNode() {
        return schemaNode;
    }
}

