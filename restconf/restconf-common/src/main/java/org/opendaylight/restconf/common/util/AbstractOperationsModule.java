/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.util;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.opendaylight.yangtools.concepts.SemVer;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.YangVersion;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchemaNode;
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
import org.opendaylight.yangtools.yang.model.api.meta.IdentifierNamespace;
import org.opendaylight.yangtools.yang.model.api.meta.StatementDefinition;
import org.opendaylight.yangtools.yang.model.api.meta.StatementSource;
import org.opendaylight.yangtools.yang.model.api.stmt.ModuleEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.ModuleStatement;

abstract class AbstractOperationsModule implements Module, ModuleEffectiveStatement {
    @Override
    public final ModuleStatement getDeclared() {
        return null;
    }

    @Override
    public final StatementDefinition statementDefinition() {
        return YangStmtMapping.MODULE;
    }

    @Override
    public final StatementSource getStatementSource() {
        return StatementSource.CONTEXT;
    }

    @Override
    public final <K, V, N extends IdentifierNamespace<K, V>> Optional<? extends V> get(final Class<N> namespace,
            final K identifier) {
        return Optional.empty();
    }

    @Override
    public final <K, V, N extends IdentifierNamespace<K, V>> Map<K, V> getAll(final Class<N> namespace) {
        return Map.of();
    }

    @Override
    public final String argument() {
        return getName();
    }

    @Override
    public final QNameModule localQNameModule() {
        return getQNameModule();
    }

    @Override
    public final Set<ModuleImport> getImports() {
        // Yeah, not accurate, but this should not be needed
        return Collections.emptySet();
    }

    @Override
    public final YangVersion getYangVersion() {
        return YangVersion.VERSION_1;
    }

    @Override
    public final Set<TypeDefinition<?>> getTypeDefinitions() {
        return Collections.emptySet();
    }

    @Override
    public final Set<GroupingDefinition> getGroupings() {
        return Collections.emptySet();
    }

    @Override
    public final Set<UsesNode> getUses() {
        return Collections.emptySet();
    }

    @Override
    public final Optional<String> getDescription() {
        return Optional.empty();
    }

    @Override
    public final Optional<String> getReference() {
        return Optional.empty();
    }

    @Override
    public final Set<NotificationDefinition> getNotifications() {
        return Collections.emptySet();
    }

    @Override
    public final Optional<SemVer> getSemanticVersion() {
        return Optional.empty();
    }

    @Override
    public final Optional<String> getOrganization() {
        return Optional.empty();
    }

    @Override
    public final Optional<String> getContact() {
        return Optional.empty();
    }

    @Override
    public final Set<Module> getSubmodules() {
        return Collections.emptySet();
    }

    @Override
    public final Set<FeatureDefinition> getFeatures() {
        return Collections.emptySet();
    }

    @Override
    public final Set<AugmentationSchemaNode> getAugmentations() {
        return Collections.emptySet();
    }

    @Override
    public final Set<RpcDefinition> getRpcs() {
        return Collections.emptySet();
    }

    @Override
    public final Set<Deviation> getDeviations() {
        return Collections.emptySet();
    }

    @Override
    public final Set<IdentitySchemaNode> getIdentities() {
        return Collections.emptySet();
    }

    @Override
    public final List<ExtensionDefinition> getExtensionSchemaNodes() {
        return Collections.emptyList();
    }
}
