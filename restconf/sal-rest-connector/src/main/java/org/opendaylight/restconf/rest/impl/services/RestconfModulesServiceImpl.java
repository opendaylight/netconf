/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.impl.services;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Set;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.rest.api.Draft09;
import org.opendaylight.restconf.rest.api.connector.RestSchemaController;
import org.opendaylight.restconf.rest.api.services.RestconfModulesService;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeAttrBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.ListNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.FeatureDefinition;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;

public class RestconfModulesServiceImpl implements RestconfModulesService {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfModulesServiceImpl.class);
    private static final SimpleDateFormat REVISION_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    private final RestSchemaController restSchemaController;

    public RestconfModulesServiceImpl(final RestSchemaController restSchemaController) {
        this.restSchemaController = restSchemaController;
    }
    @Override
    public NormalizedNodeContext getModules(final UriInfo uriInfo) {
        final Set<Module> allModules = this.restSchemaController.getAllModules();
        final MapNode allModuleMap = makeModuleMapNode(allModules);

        final SchemaContext schemaContext = this.restSchemaController.getGlobalSchema();

        final Module restconfModule = getRestconfModule();
        final DataSchemaNode modulesSchemaNode = this.restSchemaController.getRestconfModuleRestConfSchemaNode(
                restconfModule, Draft09.RestConfModule.MODULES_CONTAINER_SCHEMA_NODE);
        Preconditions.checkState(modulesSchemaNode instanceof ContainerSchemaNode);

        final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> moduleContainerBuilder = Builders
                .containerBuilder((ContainerSchemaNode) modulesSchemaNode);
        moduleContainerBuilder.withChild(allModuleMap);

        return new NormalizedNodeContext(new InstanceIdentifierContext<>(null, modulesSchemaNode, null, schemaContext),
                moduleContainerBuilder.build());
    }

    @Override
    public NormalizedNodeContext getModules(final String identifier, final UriInfo uriInfo) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public NormalizedNodeContext getModule(final String identifier, final UriInfo uriInfo) {
        // TODO Auto-generated method stub
        return null;
    }

    private MapNode makeModuleMapNode(final Set<Module> modules) {
        Preconditions.checkNotNull(modules);
        final Module restconfModule = getRestconfModule();
        final DataSchemaNode moduleSchemaNode = this.restSchemaController
                .getRestconfModuleRestConfSchemaNode(restconfModule, Draft09.RestConfModule.MODULE_LIST_SCHEMA_NODE);
        Preconditions.checkState(moduleSchemaNode instanceof ListSchemaNode);

        final CollectionNodeBuilder<MapEntryNode, MapNode> listModuleBuilder = Builders
                .mapBuilder((ListSchemaNode) moduleSchemaNode);

        for (final Module module : modules) {
            listModuleBuilder.withChild(toModuleEntryNode(module, moduleSchemaNode));
        }
        return listModuleBuilder.build();
    }

    protected MapEntryNode toModuleEntryNode(final Module module, final DataSchemaNode moduleSchemaNode) {
        Preconditions.checkArgument(moduleSchemaNode instanceof ListSchemaNode,
                "moduleSchemaNode has to be of type ListSchemaNode");
        final ListSchemaNode listModuleSchemaNode = (ListSchemaNode) moduleSchemaNode;
        final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> moduleNodeValues = Builders
                .mapEntryBuilder(listModuleSchemaNode);

        List<DataSchemaNode> instanceDataChildrenByName = ControllerContext
                .findInstanceDataChildrenByName((listModuleSchemaNode), "name");
        final DataSchemaNode nameSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        Preconditions.checkState(nameSchemaNode instanceof LeafSchemaNode);
        moduleNodeValues
                .withChild(Builders.leafBuilder((LeafSchemaNode) nameSchemaNode).withValue(module.getName()).build());

        instanceDataChildrenByName = ControllerContext.findInstanceDataChildrenByName((listModuleSchemaNode),
                "revision");
        final DataSchemaNode revisionSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        Preconditions.checkState(revisionSchemaNode instanceof LeafSchemaNode);
        final String revision = REVISION_FORMAT.format(module.getRevision());
        moduleNodeValues
                .withChild(Builders.leafBuilder((LeafSchemaNode) revisionSchemaNode).withValue(revision).build());

        instanceDataChildrenByName = ControllerContext.findInstanceDataChildrenByName((listModuleSchemaNode),
                "namespace");
        final DataSchemaNode namespaceSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        Preconditions.checkState(namespaceSchemaNode instanceof LeafSchemaNode);
        moduleNodeValues.withChild(Builders.leafBuilder((LeafSchemaNode) namespaceSchemaNode)
                .withValue(module.getNamespace().toString()).build());

        instanceDataChildrenByName = ControllerContext.findInstanceDataChildrenByName((listModuleSchemaNode),
                "feature");
        final DataSchemaNode featureSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        Preconditions.checkState(featureSchemaNode instanceof LeafListSchemaNode);
        final ListNodeBuilder<Object, LeafSetEntryNode<Object>> featuresBuilder = Builders
                .leafSetBuilder((LeafListSchemaNode) featureSchemaNode);
        for (final FeatureDefinition feature : module.getFeatures()) {
            featuresBuilder.withChild(Builders.leafSetEntryBuilder(((LeafListSchemaNode) featureSchemaNode))
                    .withValue(feature.getQName().getLocalName()).build());
        }
        moduleNodeValues.withChild(featuresBuilder.build());

        return moduleNodeValues.build();
    }

    private Module getRestconfModule() {
        final Module restconfModule = this.restSchemaController.getRestconfModule();
        if (restconfModule == null) {
            LOG.debug("ietf-restconf module was not found.");
            throw new RestconfDocumentedException("ietf-restconf module was not found.", ErrorType.APPLICATION,
                    ErrorTag.OPERATION_NOT_SUPPORTED);
        }
        return restconfModule;
    }

}
