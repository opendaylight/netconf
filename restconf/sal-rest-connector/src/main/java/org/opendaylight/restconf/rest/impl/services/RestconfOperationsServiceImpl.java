/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.impl.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.restconf.rest.api.Draft09;
import org.opendaylight.restconf.rest.api.connector.RestSchemaController;
import org.opendaylight.restconf.rest.api.services.RestconfOperationsService;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeAttrBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableLeafNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.parser.stmt.rfc6020.effective.EffectiveSchemaContext;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public class RestconfOperationsServiceImpl extends AbstractRestconfServices implements RestconfOperationsService {

    private static final Predicate<GroupingDefinition> GROUPING_FILTER = new Predicate<GroupingDefinition>() {

        @Override
        public boolean apply(final GroupingDefinition input) {
            return Draft09.RestConfModule.RESTCONF_GROUPING_SCHEMA_NODE.equals(input.getQName().getLocalName());
        }
    };

    public RestconfOperationsServiceImpl(final RestSchemaController restSchemaController) {
        super(restSchemaController);
    }

    @Override
    public NormalizedNodeContext getOperations(final UriInfo uriInfo) {
        final Set<Module> allModules = this.restSchemaController.getAllModules();
        final Module restconfModule = getRestconfModule();
        final Set<GroupingDefinition> groupings = restconfModule.getGroupings();
        final Iterable<GroupingDefinition> filterGroups = Iterables.filter(groupings, GROUPING_FILTER);
        final GroupingDefinition restconfGrouping = Iterables.getFirst(filterGroups, null);
        final QName rest = QName.create(restconfGrouping.getQName(),
                Draft09.RestConfModule.RESTCONF_CONTAINER_SCHEMA_NODE);
        final ContainerSchemaNode restContainerSchemaNode = (ContainerSchemaNode) restconfGrouping
                .getDataChildByName(rest);
        final ContainerSchemaNode containerSchemaNode = (ContainerSchemaNode) restContainerSchemaNode
                .getDataChildByName(QName.create(rest, Draft09.RestConfModule.OPERATIONS_CONTAINER_SCHEMA_NODE));

        final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> containerBuilder = Builders
                .containerBuilder(containerSchemaNode);
        final SchemaPath schemaPath = containerSchemaNode.getPath().createChild(QName.create("dummy"));

        final List<LeafNode<Object>> listOperationsAsData = new ArrayList<>();

        for (final Module module : allModules) {
            final Set<RpcDefinition> rpcs = module.getRpcs();
            for (final RpcDefinition rpc : rpcs) {
                final QName rpcQName = rpc.getQName();
                final String name = module.getName();
                final QName qName = QName.create(restconfModule.getQNameModule(), rpcQName.getLocalName());
                final Map<QName, String> attributes = new HashMap<>();
                attributes.put(rpcQName, name);
                final NodeIdentifier nodeIdentifier = new NodeIdentifier(rpcQName);
                final DataContainerChild<? extends PathArgument, ?> child = ImmutableLeafNodeBuilder.create()
                        .withAttributes(attributes).withNodeIdentifier(nodeIdentifier).build();
                containerBuilder.addChild(child);
            }
        }
        final ContainerSchemaNode operContSchemaNode = (ContainerSchemaNode) containerBuilder.build();
        final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> operContainerNode = Builders
                .containerBuilder(operContSchemaNode);

        for (final LeafNode<Object> oper : listOperationsAsData) {
            operContainerNode.withChild(oper);
        }
        final Set<Module> rpcModules = Collections.singleton(restconfModule);
        final SchemaContext resolveSchemaContext = EffectiveSchemaContext.resolveSchemaContext(rpcModules);

        final InstanceIdentifierContext<?> fakeIICx = new InstanceIdentifierContext<>(null, operContSchemaNode,
                null, resolveSchemaContext);
        return new NormalizedNodeContext(fakeIICx, operContainerNode.build());
    }


    @Override
    public NormalizedNodeContext getOperations(final String identifier, final UriInfo uriInfo) {
        // TODO Auto-generated method stub
        return null;
    }

}
