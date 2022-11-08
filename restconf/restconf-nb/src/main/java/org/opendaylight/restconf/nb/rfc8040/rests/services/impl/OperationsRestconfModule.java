/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.meta.EffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.DataTreeEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.ModuleEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaTreeEffectiveStatement;

@Deprecated(forRemoval = true, since = "4.0.0")
final class OperationsRestconfModule extends AbstractOperationsModule {
    // There is no need to intern this nor add a revision, as we are providing the corresponding context anyway
    static final @NonNull QNameModule NAMESPACE =
            QNameModule.create(XMLNamespace.of("urn:ietf:params:xml:ns:yang:ietf-restconf"));

    private final OperationsContainerSchemaNode operations;

    OperationsRestconfModule(final OperationsContainerSchemaNode operations) {
        this.operations = requireNonNull(operations);
    }

    @Override
    public String getName() {
        return "ietf-restconf";
    }

    @Override
    public QNameModule getQNameModule() {
        return NAMESPACE;
    }

    @Override
    public String getPrefix() {
        return "rc";
    }

    @Override
    public Collection<DataSchemaNode> getChildNodes() {
        return List.of(operations);
    }

    @Override
    public DataSchemaNode dataChildByName(final QName name) {
        return name.equals(operations.getQName()) ? operations : null;
    }

    @Override
    public List<EffectiveStatement<?, ?>> effectiveSubstatements() {
        return List.of(operations);
    }

    @Override
    public ConformanceType conformance() {
        return ConformanceType.IMPLEMENT;
    }

    @Override
    public Optional<DataTreeEffectiveStatement<?>> findDataTreeNode(final QName qname) {
        return qname.equals(operations.getQName()) ? Optional.of(operations) : Optional.empty();
    }

    @Override
    public Optional<SchemaTreeEffectiveStatement<?>> findSchemaTreeNode(final QName qname) {
        return qname.equals(operations.getQName()) ? Optional.of(operations) : Optional.empty();
    }

    @Override
    public Collection<DataTreeEffectiveStatement<?>> dataTreeNodes() {
        return List.of(operations);
    }

    @Override
    public Collection<SchemaTreeEffectiveStatement<?>> schemaTreeNodes() {
        return List.of(operations);
    }

    @Override
    public Optional<ModuleEffectiveStatement> findReachableModule(final String prefix) {
        return Optional.empty();
    }

    @Override
    public Collection<Entry<String, ModuleEffectiveStatement>> reachableModules() {
        return List.of();
    }

    @Override
    public Optional<String> findNamespacePrefix(final QNameModule namespace) {
        return Optional.empty();
    }

    @Override
    public Collection<Entry<QNameModule, String>> namespacePrefixes() {
        return List.of();
    }
}
