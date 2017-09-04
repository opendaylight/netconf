/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.base.services.impl;

import com.google.common.base.Preconditions;
import com.google.common.collect.ForwardingObject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.net.URI;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchema;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Deviation;
import org.opendaylight.yangtools.yang.model.api.ExtensionDefinition;
import org.opendaylight.yangtools.yang.model.api.FeatureDefinition;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.IdentitySchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.ModuleImport;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.UnknownSchemaNode;
import org.opendaylight.yangtools.yang.model.api.UsesNode;

final class FakeImportedModule extends ForwardingObject implements Module {

    private final Module delegate;

    FakeImportedModule(final Module delegate) {
        this.delegate = Preconditions.checkNotNull(delegate);
    }

    @Override
    protected Module delegate() {
        return delegate;
    }

    @Override
    public Set<TypeDefinition<?>> getTypeDefinitions() {
        return ImmutableSet.of();
    }

    @Override
    public Collection<DataSchemaNode> getChildNodes() {
        return ImmutableList.of();
    }

    @Override
    public Set<GroupingDefinition> getGroupings() {
        return ImmutableSet.of();
    }

    @Override
    public DataSchemaNode getDataChildByName(final QName name) {
        return null;
    }

    @Override
    public Set<UsesNode> getUses() {
        return ImmutableSet.of();
    }

    @Override
    public String getModuleSourcePath() {
        return null;
    }

    @Override
    public QNameModule getQNameModule() {
        return delegate.getQNameModule();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public URI getNamespace() {
        return delegate.getNamespace();
    }

    @Override
    public Date getRevision() {
        return delegate.getRevision();
    }

    @Override
    public String getPrefix() {
        return delegate.getPrefix();
    }

    @Override
    public String getYangVersion() {
        return delegate.getYangVersion();
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public String getReference() {
        return delegate.getReference();
    }

    @Override
    public String getOrganization() {
        return delegate.getOrganization();
    }

    @Override
    public String getContact() {
        return delegate.getContact();
    }

    @Override
    public Set<ModuleImport> getImports() {
        return ImmutableSet.of();
    }

    @Override
    public Set<Module> getSubmodules() {
        return ImmutableSet.of();
    }

    @Override
    public Set<FeatureDefinition> getFeatures() {
        return ImmutableSet.of();
    }

    @Override
    public Set<AugmentationSchema> getAugmentations() {
        return ImmutableSet.of();
    }

    @Override
    public Set<RpcDefinition> getRpcs() {
        return ImmutableSet.of();
    }

    @Override
    public Set<Deviation> getDeviations() {
        return ImmutableSet.of();
    }

    @Override
    public Set<IdentitySchemaNode> getIdentities() {
        return ImmutableSet.of();
    }

    @Override
    public List<ExtensionDefinition> getExtensionSchemaNodes() {
        return ImmutableList.of();
    }

    @Override
    public List<UnknownSchemaNode> getUnknownSchemaNodes() {
        return ImmutableList.of();
    }

    @Override
    public String getSource() {
        return null;
    }
}
