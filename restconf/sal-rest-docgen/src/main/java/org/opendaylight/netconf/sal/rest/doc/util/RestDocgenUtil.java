/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.util;

import java.util.HashMap;
//import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaTreeEffectiveStatement;
import org.opendaylight.yangtools.yang.model.spi.DefaultSchemaTreeInference;

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
    public static String resolvePathArgumentsName(final SchemaNode node, final EffectiveModelContext schemaContext) {
        Absolute absPath =  Absolute.of(node.getQName());
        DefaultSchemaTreeInference inferredPath = DefaultSchemaTreeInference.of(schemaContext, absPath);
        List<SchemaTreeEffectiveStatement<?>> listOfStatements = inferredPath.statementPath();
        //Get last element and its parent
        QName nodeQName = null;
        QName parentQName = null;
        if (! listOfStatements.isEmpty() && listOfStatements.size() > 2) {
            //SchemaTreeEffectiveStatement lastNode = listOfStatements.get(listOfStatements.size()-1);
            nodeQName = listOfStatements.get(listOfStatements.size() - 1).getIdentifier();
            parentQName = listOfStatements.get(listOfStatements.size() - 2).getIdentifier();
        }

        if (isEqualNamespaceAndRevision(parentQName, nodeQName)) {
            return node.getQName().getLocalName();
        } else {
            return resolveFullNameFromNode(node, schemaContext);
        }
    }

    private static synchronized String resolveFullNameFromNode(final SchemaNode node,
            final SchemaContext schemaContext) {
        final XMLNamespace namespace = node.getQName().getNamespace();
        final Optional<Revision> revision = node.getQName().getRevision();

        Map<Optional<Revision>, Module> revisionToModule =
            NAMESPACE_AND_REVISION_TO_MODULE.computeIfAbsent(namespace, k -> new HashMap<>());
        Module module =
            revisionToModule.computeIfAbsent(revision, k -> schemaContext.findModule(namespace, k).orElse(null));
        if (module != null) {
            return module.getName() + ":" + node.getQName().getLocalName();
        }
        return node.getQName().getLocalName();
    }

    public static String resolveNodesName(final SchemaNode node, final Module module,
            final SchemaContext schemaContext) {
        if (node.getQName().getNamespace().equals(module.getQNameModule().getNamespace())
                && node.getQName().getRevision().equals(module.getQNameModule().getRevision())) {
            return node.getQName().getLocalName();
        } else {
            return resolveFullNameFromNode(node, schemaContext);
        }
    }

    private static boolean isEqualNamespaceAndRevision(final QName parentQName, final QName nodeQName) {
        if (parentQName == null) {
            return nodeQName == null;
        }
        return parentQName.getNamespace().equals(nodeQName.getNamespace())
                && parentQName.getRevision().equals(nodeQName.getRevision());
    }

}
