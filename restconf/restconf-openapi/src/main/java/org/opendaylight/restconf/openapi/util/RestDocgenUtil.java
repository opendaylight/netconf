/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;

public final class RestDocgenUtil {
    private static final Map<XMLNamespace, Map<Optional<Revision>, Module>> NAMESPACE_AND_REVISION_TO_MODULE =
        new HashMap<>();

    private RestDocgenUtil() {
        // Hidden on purpose
    }

    /**
     * Resolve path argument name for {@code node}.
     *
     * <p>The name can contain also prefix which consists of module name followed by colon. The module
     * prefix is presented if namespace of {@code node} and its parent is different. In other cases
     * only name of {@code node} is returned.
     *
     * @return name of {@code node}
     */
    public static String resolvePathArgumentsName(@NonNull final QName node, @NonNull final QName parent,
                                                  @NonNull final EffectiveModelContext modelContext) {
        if (isEqualNamespaceAndRevision(node, parent)) {
            return node.getLocalName();
        } else {
            return resolveFullNameFromNode(node, modelContext);
        }
    }

    private static boolean isEqualNamespaceAndRevision(final QName node, final QName parent) {
        return parent.getNamespace().equals(node.getNamespace())
                && parent.getRevision().equals(node.getRevision());
    }

    public static String resolveFullNameFromNode(final QName node, final EffectiveModelContext modelContext) {
        final XMLNamespace namespace = node.getNamespace();
        final Optional<Revision> revision = node.getRevision();

        final Map<Optional<Revision>, Module> revisionToModule =
            NAMESPACE_AND_REVISION_TO_MODULE.computeIfAbsent(namespace, k -> new HashMap<>());
        final Module module = revisionToModule.computeIfAbsent(revision,
                k -> modelContext.findModule(namespace, k).orElse(null));
        if (module != null) {
            return module.getName() + ":" + node.getLocalName();
        }
        return node.getLocalName();
    }

    public static Collection<? extends DataSchemaNode> widthList(final DataNodeContainer node, final int width) {
        if (width > 0) {
            return node.getChildNodes().stream().limit(width).toList(); // limit children to width
        }
        return node.getChildNodes(); // width not applied - processing all children
    }
}
