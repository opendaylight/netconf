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
import java.util.Optional;
import java.util.Set;
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

final class OperationsRestconfModule implements Module  {
    // There is no need to intern this nor add a revision, as we are providing the corresponding context anyway
    static final QNameModule NAMESPACE = QNameModule.create(URI.create("urn:ietf:params:xml:ns:yang:ietf-restconf"));

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
    public Collection<DataSchemaNode> getChildNodes() {
        return Collections.singleton(operations);
    }

    @Override
    public Optional<DataSchemaNode> findDataChildByName(final QName name) {
        // TODO Auto-generated method stub
        return null;
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
