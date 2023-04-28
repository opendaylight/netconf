/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.common.mdsal;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.concepts.Delegator;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.ExtensionDefinition;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.IdentitySchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.UnknownSchemaNode;
import org.opendaylight.yangtools.yang.model.api.stmt.ModuleEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaTreeEffectiveStatement;

/**
 * A simple proxy, overriding #getQName() to a value containing revision.
 */
final class ProxyEffectiveModelContext implements EffectiveModelContext, Delegator<EffectiveModelContext> {
    private final @NonNull EffectiveModelContext delegate;

    ProxyEffectiveModelContext(final EffectiveModelContext delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public EffectiveModelContext getDelegate() {
        return delegate;
    }

    @Override
    @Deprecated
    public QName getQName() {
        return NormalizedDataUtil.NETCONF_DATA_QNAME;
    }

    @Override
    public Optional<SchemaTreeEffectiveStatement<?>> findSchemaTreeNode(final SchemaNodeIdentifier path) {
        return delegate.findSchemaTreeNode(path);
    }

    @Override
    public <T> Optional<T> findSchemaTreeNode(final Class<T> type, final SchemaNodeIdentifier path) {
        return delegate.findSchemaTreeNode(type, path);
    }

    @Override
    public Map<QNameModule, ModuleEffectiveStatement> getModuleStatements() {
        return delegate.getModuleStatements();
    }

    @Override
    public Optional<ModuleEffectiveStatement> findModuleStatement(final QNameModule moduleName) {
        return delegate.findModuleStatement(moduleName);
    }

    @Override
    public Optional<ModuleEffectiveStatement> findModuleStatement(final QName moduleName) {
        return delegate.findModuleStatement(moduleName);
    }

    @Override
    public ModuleEffectiveStatement getModuleStatement(final QNameModule moduleName) {
        return delegate.getModuleStatement(moduleName);
    }

    @Override
    public ModuleEffectiveStatement getModuleStatement(final QName moduleName) {
        return delegate.getModuleStatement(moduleName);
    }

    @Override
    public Collection<? extends DataSchemaNode> getDataDefinitions() {
        return delegate.getDataDefinitions();
    }

    @Override
    public Collection<? extends Module> getModules() {
        return delegate.getModules();
    }

    @Override
    public Collection<? extends RpcDefinition> getOperations() {
        return delegate.getOperations();
    }

    @Override
    public Collection<? extends ExtensionDefinition> getExtensions() {
        return delegate.getExtensions();
    }

    @Override
    public Optional<Module> findModule(final QNameModule qnameModule) {
        return delegate.findModule(qnameModule);
    }

    @Override
    public Collection<? extends Module> findModules(final XMLNamespace namespace) {
        return delegate.findModules(namespace);
    }

    @Override
    public Optional<NotificationDefinition> findNotification(final QName qname) {
        return delegate.findNotification(qname);
    }

    @Override
    public Optional<DataSchemaNode> findDataTreeChild(final QName name) {
        return delegate.findDataTreeChild(name);
    }

    @Override
    public Collection<? extends IdentitySchemaNode> getDerivedIdentities(final IdentitySchemaNode identity) {
        return delegate.getDerivedIdentities(identity);
    }

    @Override
    public Collection<? extends UnknownSchemaNode> getUnknownSchemaNodes() {
        return delegate.getUnknownSchemaNodes();
    }

    @Override
    public Collection<? extends TypeDefinition<?>> getTypeDefinitions() {
        return delegate.getTypeDefinitions();
    }

    @Override
    public Collection<? extends DataSchemaNode> getChildNodes() {
        return delegate.getChildNodes();
    }

    @Override
    public Collection<? extends GroupingDefinition> getGroupings() {
        return delegate.getGroupings();
    }

    @Override
    public DataSchemaNode dataChildByName(final QName name) {
        return delegate.dataChildByName(name);
    }

    @Override
    public Collection<? extends NotificationDefinition> getNotifications() {
        return delegate.getNotifications();
    }
}
