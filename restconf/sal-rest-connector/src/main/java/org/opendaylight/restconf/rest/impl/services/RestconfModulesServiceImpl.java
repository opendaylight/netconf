/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.impl.services;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.rest.api.Draft09;
import org.opendaylight.restconf.rest.api.connector.RestSchemaController;
import org.opendaylight.restconf.rest.api.services.RestconfModulesService;
import org.opendaylight.restconf.utils.RestSchemaControllerUtil;
import org.opendaylight.yangtools.yang.common.QName;
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
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class RestconfModulesServiceImpl extends AbstractRestconfServices implements RestconfModulesService {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfModulesServiceImpl.class);
    private static final SimpleDateFormat REVISION_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    public RestconfModulesServiceImpl(final RestSchemaController restSchemaController) {
        super(restSchemaController);
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

    /**
     * Valid only for mount point
     */
    @Override
    public NormalizedNodeContext getModules(final String identifier, final UriInfo uriInfo) {
        Preconditions.checkNotNull(identifier);
        if (!identifier.contains(RestSchemaControllerUtil.MOUNT)) {
            final String errMsg = "URI has bad format. If modules behind mount point should be showed,"
                    + " URI has to end with " + RestSchemaControllerUtil.MOUNT;
            LOG.debug(errMsg + " for " + identifier);
            throw new RestconfDocumentedException(errMsg, ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        final InstanceIdentifierContext<?> mountPointIdentifier = this.restSchemaController
                .toMountPointIdentifier(identifier);
        final DOMMountPoint mountPoint = mountPointIdentifier.getMountPoint();
        final Set<Module> modules = this.restSchemaController.getAllModules(mountPoint);
        final MapNode mountPointModulesMap = makeModuleMapNode(modules);

        final Module restconfModule = getRestconfModule();
        final DataSchemaNode modulesSchemaNode = this.restSchemaController.getRestconfModuleRestConfSchemaNode(
                restconfModule, Draft09.RestConfModule.MODULES_CONTAINER_SCHEMA_NODE);
        Preconditions.checkState(modulesSchemaNode instanceof ContainerSchemaNode);

        final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> moduleContainerBuilder = Builders
                .containerBuilder((ContainerSchemaNode) modulesSchemaNode);
        moduleContainerBuilder.withChild(mountPointModulesMap);

        return new NormalizedNodeContext(
                new InstanceIdentifierContext<>(null, modulesSchemaNode, mountPoint,
                        this.restSchemaController.getGlobalSchema()), moduleContainerBuilder.build());
    }

    @Override
    public NormalizedNodeContext getModule(final String identifier, final UriInfo uriInfo) {
        Preconditions.checkNotNull(identifier);
        final QName moduleNameAndRevision = getModuleNameAndRevision(identifier);
        Module module = null;
        DOMMountPoint mountPoint = null;
        final SchemaContext schemaContext;
        if (identifier.contains(RestSchemaControllerUtil.MOUNT)) {
            final InstanceIdentifierContext<?> mountPointIdentifier = this.restSchemaController
                    .toMountPointIdentifier(identifier);
            mountPoint = mountPointIdentifier.getMountPoint();
            module = this.restSchemaController.findModuleByNameAndRevision(mountPoint, moduleNameAndRevision);
            schemaContext = mountPoint.getSchemaContext();
        } else {
            module = this.restSchemaController.findModuleByNameAndRevision(moduleNameAndRevision);
            schemaContext = this.restSchemaController.getGlobalSchema();
        }

        if (module == null) {
            final String errMsg = "Module with name '" + moduleNameAndRevision.getLocalName() + "' and revision '"
                    + moduleNameAndRevision.getRevision() + "' was not found.";
            LOG.debug(errMsg);
            throw new RestconfDocumentedException(errMsg, ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ELEMENT);
        }

        final Module restconfModule = getRestconfModule();
        final Set<Module> modules = Collections.singleton(module);
        final MapNode moduleMap = makeModuleMapNode(modules);

        final DataSchemaNode moduleSchemaNode = this.restSchemaController
                .getRestconfModuleRestConfSchemaNode(restconfModule, Draft09.RestConfModule.MODULE_LIST_SCHEMA_NODE);
        Preconditions.checkState(moduleSchemaNode instanceof ListSchemaNode);

        return new NormalizedNodeContext(
                new InstanceIdentifierContext<>(null, moduleSchemaNode, mountPoint, schemaContext), moduleMap);
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

        List<DataSchemaNode> instanceDataChildrenByName = RestSchemaControllerUtil
                .findInstanceDataChildrenByName((listModuleSchemaNode), "name");
        final DataSchemaNode nameSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        Preconditions.checkState(nameSchemaNode instanceof LeafSchemaNode);
        moduleNodeValues
                .withChild(Builders.leafBuilder((LeafSchemaNode) nameSchemaNode).withValue(module.getName()).build());

        instanceDataChildrenByName = RestSchemaControllerUtil.findInstanceDataChildrenByName((listModuleSchemaNode),
                "revision");
        final DataSchemaNode revisionSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        Preconditions.checkState(revisionSchemaNode instanceof LeafSchemaNode);
        final String revision = REVISION_FORMAT.format(module.getRevision());
        moduleNodeValues
                .withChild(Builders.leafBuilder((LeafSchemaNode) revisionSchemaNode).withValue(revision).build());

        instanceDataChildrenByName = RestSchemaControllerUtil.findInstanceDataChildrenByName((listModuleSchemaNode),
                "namespace");
        final DataSchemaNode namespaceSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        Preconditions.checkState(namespaceSchemaNode instanceof LeafSchemaNode);
        moduleNodeValues.withChild(Builders.leafBuilder((LeafSchemaNode) namespaceSchemaNode)
                .withValue(module.getNamespace().toString()).build());

        instanceDataChildrenByName = RestSchemaControllerUtil.findInstanceDataChildrenByName((listModuleSchemaNode),
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

    private static QName getModuleNameAndRevision(final String identifier) {
        final int mountIndex = identifier.indexOf(RestSchemaControllerUtil.MOUNT);
        String moduleNameAndRevision = "";
        if (mountIndex >= 0) {
            moduleNameAndRevision = identifier.substring(mountIndex + RestSchemaControllerUtil.MOUNT.length());
        } else {
            moduleNameAndRevision = identifier;
        }

        final Splitter splitter = Splitter.on("/").omitEmptyStrings();
        final Iterable<String> split = splitter.split(moduleNameAndRevision);
        final List<String> pathArgs = Lists.<String> newArrayList(split);
        if (pathArgs.size() < 2) {
            LOG.debug("URI has bad format. It should be \'moduleName/yyyy-MM-dd\' " + identifier);
            throw new RestconfDocumentedException(
                    "URI has bad format. End of URI should be in format \'moduleName/yyyy-MM-dd\'", ErrorType.PROTOCOL,
                    ErrorTag.INVALID_VALUE);
        }

        try {
            final String moduleName = pathArgs.get(0);
            final String revision = pathArgs.get(1);
            final Date moduleRevision = REVISION_FORMAT.parse(revision);
            return QName.create(null, moduleRevision, moduleName);
        } catch (final ParseException e) {
            LOG.debug("URI has bad format. It should be \'moduleName/yyyy-MM-dd\' " + identifier);
            throw new RestconfDocumentedException("URI has bad format. It should be \'moduleName/yyyy-MM-dd\'",
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }
    }

}
