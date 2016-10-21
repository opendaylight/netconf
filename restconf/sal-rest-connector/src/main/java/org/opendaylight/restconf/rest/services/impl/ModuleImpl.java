/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.services.impl;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchema;
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
 * new schema context
 *
 */
class ModuleImpl implements Module {

    private final List<DataSchemaNode> listChild = new ArrayList<>();
    static QNameModule moduleQName;
    static {
        Date date = null;
        try {
            date = SimpleDateFormatUtil.getRevisionFormat().parse("2016-06-28");
        } catch (final ParseException e) {
            throw new RestconfDocumentedException("Problem while parsing revision.", e);
        }
        try {
            moduleQName = QNameModule.create(new URI("urn:ietf:params:xml:ns:yang:ietf-restconf"), date);
        } catch (final URISyntaxException e) {
            throw new RestconfDocumentedException("Problem while creating URI.", e);
        }
    }

    /**
     * Set container for this module
     *
     * @param fakeContSchNode
     *            - fake container schema node
     *
     */
    public ModuleImpl(final ContainerSchemaNode fakeContSchNode) {
        this.listChild.add(fakeContSchNode);
    }

    @Override
    public Set<TypeDefinition<?>> getTypeDefinitions() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

    @Override
    public Collection<DataSchemaNode> getChildNodes() {
        return this.listChild;
    }

    @Override
    public Set<GroupingDefinition> getGroupings() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

    @Override
    public DataSchemaNode getDataChildByName(final QName name) {
        for (final DataSchemaNode node : this.listChild) {
            if (node.getQName().equals(name)) {
                return node;
            }
        }
        throw new RestconfDocumentedException(name + " is not in child of " + ModuleImpl.moduleQName);
    }

    @Override
    public DataSchemaNode getDataChildByName(final String name) {
        for (final DataSchemaNode node : this.listChild) {
            if (node.getQName().getLocalName().equals(name)) {
                return node;
            }
        }
        throw new RestconfDocumentedException(name + " is not in child of " + ModuleImpl.moduleQName);
    }

    @Override
    public Set<UsesNode> getUses() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

    @Override
    public String getModuleSourcePath() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

    @Override
    public QNameModule getQNameModule() {
        return moduleQName;
    }

    @Override
    public String getName() {
        return "ietf-restconf";
    }

    @Override
    public URI getNamespace() {
        return moduleQName.getNamespace();
    }

    @Override
    public Date getRevision() {
        return moduleQName.getRevision();
    }

    @Override
    public String getPrefix() {
        return "restconf";
    }

    @Override
    public String getYangVersion() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

    @Override
    public String getDescription() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

    @Override
    public String getReference() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

    @Override
    public String getOrganization() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

    @Override
    public String getContact() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

    @Override
    public Set<ModuleImport> getImports() {
        return new HashSet<>();
    }

    @Override
    public Set<Module> getSubmodules() {
        return new HashSet<>();
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
    public Set<AugmentationSchema> getAugmentations() {
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
    public String getSource() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

}
