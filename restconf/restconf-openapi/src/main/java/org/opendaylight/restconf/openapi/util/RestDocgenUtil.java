/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public final class RestDocgenUtil {

    private RestDocgenUtil() {
    }

    private static final Map<XMLNamespace, Map<Optional<Revision>, Module>> NAMESPACE_AND_REVISION_TO_MODULE =
        new HashMap<>();

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
                                                  @NonNull final EffectiveModelContext schemaContext) {
        if (isEqualNamespaceAndRevision(node, parent)) {
            return node.getLocalName();
        } else {
            return resolveFullNameFromNode(node, schemaContext);
        }
    }

    /*
     * Resolve full name according to module and node namespace and revision equality.
     *
     * @deprecated Most likely this method is useless because when we are going from module to its direct children
     * there is no need for reasoning if we should use full name.
     */
    @Deprecated(forRemoval = true)
    public static String resolveNodesName(final SchemaNode node, final Module module,
            final SchemaContext schemaContext) {
        if (node.getQName().getNamespace().equals(module.getQNameModule().getNamespace())
                && node.getQName().getRevision().equals(module.getQNameModule().getRevision())) {
            return node.getQName().getLocalName();
        } else {
            return resolveFullNameFromNode(node.getQName(), schemaContext);
        }
    }

    private static boolean isEqualNamespaceAndRevision(final QName node, final QName parent) {
        return parent.getNamespace().equals(node.getNamespace())
                && parent.getRevision().equals(node.getRevision());
    }

    private static String resolveFullNameFromNode(final QName node, final SchemaContext schemaContext) {
        final XMLNamespace namespace = node.getNamespace();
        final Optional<Revision> revision = node.getRevision();

        final Map<Optional<Revision>, Module> revisionToModule =
            NAMESPACE_AND_REVISION_TO_MODULE.computeIfAbsent(namespace, k -> new HashMap<>());
        final Module module = revisionToModule.computeIfAbsent(revision,
                k -> schemaContext.findModule(namespace, k).orElse(null));
        if (module != null) {
            return module.getName() + ":" + node.getLocalName();
        }
        return node.getLocalName();
    }
}
