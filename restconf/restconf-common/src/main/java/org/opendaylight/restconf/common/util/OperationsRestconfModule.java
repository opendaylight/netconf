/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.util;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.concepts.SemVer;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.YangVersion;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Deviation;
import org.opendaylight.yangtools.yang.model.api.ExtensionDefinition;
import org.opendaylight.yangtools.yang.model.api.FeatureDefinition;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.IdentitySchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.ModuleImport;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.UsesNode;
import org.opendaylight.yangtools.yang.model.api.YangStmtMapping;
import org.opendaylight.yangtools.yang.model.api.meta.EffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.meta.IdentifierNamespace;
import org.opendaylight.yangtools.yang.model.api.meta.StatementDefinition;
import org.opendaylight.yangtools.yang.model.api.meta.StatementSource;
import org.opendaylight.yangtools.yang.model.api.stmt.ModuleEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.ModuleStatement;

final class OperationsRestconfModule implements Module, ModuleEffectiveStatement {
    // There is no need to intern this nor add a revision, as we are providing the corresponding context anyway
    static final @NonNull QNameModule NAMESPACE =
            QNameModule.create(URI.create("urn:ietf:params:xml:ns:yang:ietf-restconf"));

    private final OperationsContainerSchemaNode operations;

    OperationsRestconfModule(final OperationsContainerSchemaNode operations) {
        this.operations = requireNonNull(operations);
    }

    @Override
    public String argument() {
        return "ietf-restconf";
    }

    @Override
    public QNameModule localQNameModule() {
        return NAMESPACE;
    }

    @Override
    public String getName() {
        return argument();
    }

    @Override
    public QNameModule getQNameModule() {
        return localQNameModule();
    }

    @Override
    public Collection<DataSchemaNode> getChildNodes() {
        return Collections.singleton(operations);
    }

    @Override
    public Optional<DataSchemaNode> findDataChildByName(final QName name) {
        return operations.getQName().equals(requireNonNull(name)) ? Optional.of(operations) : Optional.empty();
    }

    @Override
    public String getPrefix() {
        return "rc";
    }

    @Override
    public YangVersion getYangVersion() {
        return YangVersion.VERSION_1;
    }

    @Override
    public Collection<? extends EffectiveStatement<?, ?>> effectiveSubstatements() {
        return List.of();
    }

    @Override
    public StatementDefinition statementDefinition() {
        return YangStmtMapping.MODULE;
    }

    @Override
    public StatementSource getStatementSource() {
        return StatementSource.CONTEXT;
    }

    @Override
    public ModuleStatement getDeclared() {
        return null;
    }

    @Override
    public <K, V, N extends IdentifierNamespace<K, V>> Optional<? extends V> get(final Class<N> namespace,
            final K identifier) {
        return Optional.empty();
    }

    @Override
    public <K, V, N extends IdentifierNamespace<K, V>> Map<K, V> getAll(final Class<N> namespace) {
        return Map.of();
    }

    @Override
    public Set<TypeDefinition<?>> getTypeDefinitions() {
        return Collections.emptySet();
    }

    @Override
    public Set<GroupingDefinition> getGroupings() {
        return Collections.emptySet();
    }

    @Override
    public Set<ModuleImport> getImports() {
        // Yeah, not accurate, but this should not be needed
        return Collections.emptySet();
    }

    @Override
    public Set<UsesNode> getUses() {
        return Collections.emptySet();
    }

    @Override
    public Optional<String> getDescription() {
        return Optional.empty();
    }

    @Override
    public Optional<String> getReference() {
        return Optional.empty();
    }

    @Override
    public Set<NotificationDefinition> getNotifications() {
        return Collections.emptySet();
    }

    @Override
    public Optional<SemVer> getSemanticVersion() {
        return Optional.empty();
    }

    @Override
    public Optional<String> getOrganization() {
        return Optional.empty();
    }

    @Override
    public Optional<String> getContact() {
        return Optional.empty();
    }

    @Override
    public Set<Module> getSubmodules() {
        return Collections.emptySet();
    }

    @Override
    public Set<FeatureDefinition> getFeatures() {
        return Collections.emptySet();
    }

    @Override
    public Set<AugmentationSchemaNode> getAugmentations() {
        return Collections.emptySet();
    }

    @Override
    public Set<RpcDefinition> getRpcs() {
        return Collections.emptySet();
    }

    @Override
    public Set<Deviation> getDeviations() {
        return Collections.emptySet();
    }

    @Override
    public Set<IdentitySchemaNode> getIdentities() {
        return Collections.emptySet();
    }

    @Override
    public List<ExtensionDefinition> getExtensionSchemaNodes() {
        return Collections.emptyList();
    }
}
