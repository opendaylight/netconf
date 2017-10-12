/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl;

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.yangtools.concepts.SemVer;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.YangVersion;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
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
import org.opendaylight.yangtools.yang.model.api.UnknownSchemaNode;
import org.opendaylight.yangtools.yang.model.api.UsesNode;

/**
 * Special case only use by GET restconf/operations (since moment of old Yang
 * parser and old yang model API removal) to build and use fake module to create
 * new schema context.
 */
final class FakeRestconfModule implements Module {

    static final QNameModule QNAME = QNameModule.create(URI.create("urn:ietf:params:xml:ns:yang:ietf-restconf"),
        Revision.of("2016-06-28")).intern();

    private final Collection<DataSchemaNode> children;
    private final ImmutableSet<ModuleImport> imports;

    /**
     * Instantiate a new fake module.
     *
     * @param neededModules needed import statements
     * @param child fake child container
     */
    FakeRestconfModule(final Collection<Module> neededModules, final ContainerSchemaNode child) {
        this.children = ImmutableList.of(child);
        this.imports = ImmutableSet.copyOf(Collections2.transform(neededModules, FakeModuleImport::new));
    }

    @Override
    public Set<TypeDefinition<?>> getTypeDefinitions() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

    @Override
    public Collection<DataSchemaNode> getChildNodes() {
        return this.children;
    }

    @Override
    public Set<GroupingDefinition> getGroupings() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

    @Override
    public Optional<DataSchemaNode> findDataChildByName(final QName name) {
        for (final DataSchemaNode node : this.children) {
            if (node.getQName().equals(name)) {
                return Optional.of(node);
            }
        }
        throw new RestconfDocumentedException(name + " is not in child of " + FakeRestconfModule.QNAME);
    }

    @Override
    public Set<UsesNode> getUses() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

    @Override
    public QNameModule getQNameModule() {
        return QNAME;
    }

    @Override
    public String getName() {
        return "ietf-restconf";
    }

    @Override
    public URI getNamespace() {
        return QNAME.getNamespace();
    }

    @Override
    public Optional<Revision> getRevision() {
        return QNAME.getRevision();
    }

    @Override
    public String getPrefix() {
        return "restconf";
    }

    @Override
    public YangVersion getYangVersion() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

    @Override
    public Optional<String> getDescription() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

    @Override
    public Optional<String> getReference() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

    @Override
    public Optional<String> getOrganization() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

    @Override
    public Optional<String> getContact() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

    @Override
    public Set<ModuleImport> getImports() {
        return imports;
    }

    @Override
    public Set<Module> getSubmodules() {
        return ImmutableSet.of();
    }

    @Override
    public Set<FeatureDefinition> getFeatures() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

    @Override
    public Set<NotificationDefinition> getNotifications() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

    @Override
    public Set<AugmentationSchemaNode> getAugmentations() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

    @Override
    public Set<RpcDefinition> getRpcs() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

    @Override
    public Set<Deviation> getDeviations() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

    @Override
    public Set<IdentitySchemaNode> getIdentities() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

    @Override
    public List<ExtensionDefinition> getExtensionSchemaNodes() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

    @Override
    public List<UnknownSchemaNode> getUnknownSchemaNodes() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

    @Override
    public Optional<SemVer> getSemanticVersion() {
        throw new UnsupportedOperationException("Not supported operations.");
    }
}
