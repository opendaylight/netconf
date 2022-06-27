/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.util;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.UnresolvedQName;
import org.opendaylight.yangtools.yang.common.UnresolvedQName.Unqualified;
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
import org.opendaylight.yangtools.yang.model.api.Submodule;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.UsesNode;
import org.opendaylight.yangtools.yang.model.api.YangStmtMapping;
import org.opendaylight.yangtools.yang.model.api.meta.IdentifierNamespace;
import org.opendaylight.yangtools.yang.model.api.meta.StatementDefinition;
import org.opendaylight.yangtools.yang.model.api.meta.StatementOrigin;
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
    public final StatementOrigin statementOrigin() {
        return StatementOrigin.CONTEXT;
    }

    @Override
    public final <K, V, N extends IdentifierNamespace<K, V>> Optional<V> get(final Class<N> namespace,
            final K identifier) {
        return Optional.empty();
    }

    @Override
    public final <K, V, N extends IdentifierNamespace<K, V>> Map<K, V> getAll(final Class<N> namespace) {
        return Map.of();
    }

    @Override
    public final Unqualified argument() {
        return UnresolvedQName.unqualified(getName());
    }

    @Override
    public final QNameModule localQNameModule() {
        return getQNameModule();
    }

    @Override
    public final Collection<? extends @NonNull ModuleImport> getImports() {
        // Yeah, not accurate, but this should not be needed
        return Collections.emptySet();
    }

    @Override
    public final YangVersion getYangVersion() {
        return YangVersion.VERSION_1;
    }

    @Override
    public final Collection<? extends TypeDefinition<?>> getTypeDefinitions() {
        return Collections.emptySet();
    }

    @Override
    public final Collection<? extends GroupingDefinition> getGroupings() {
        return Collections.emptySet();
    }

    @Override
    public final Collection<? extends UsesNode> getUses() {
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
    public final Collection<? extends NotificationDefinition> getNotifications() {
        return Collections.emptySet();
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
    public final Collection<? extends @NonNull Submodule> getSubmodules() {
        return Collections.emptySet();
    }

    @Override
    public final Collection<? extends @NonNull FeatureDefinition> getFeatures() {
        return Collections.emptySet();
    }

    @Override
    public final Collection<? extends @NonNull AugmentationSchemaNode> getAugmentations() {
        return Collections.emptySet();
    }

    @Override
    public final Collection<? extends @NonNull RpcDefinition> getRpcs() {
        return Collections.emptySet();
    }

    @Override
    public final Collection<? extends @NonNull Deviation> getDeviations() {
        return Collections.emptySet();
    }

    @Override
    public final Collection<? extends @NonNull IdentitySchemaNode> getIdentities() {
        return Collections.emptySet();
    }

    @Override
    public final Collection<? extends @NonNull ExtensionDefinition> getExtensionSchemaNodes() {
        return Collections.emptyList();
    }

    @Override
    public final ModuleEffectiveStatement asEffectiveStatement() {
        throw new UnsupportedOperationException();
    }

    @Override
    public final ConformanceType conformance() {
        throw new UnsupportedOperationException();
    }
}
