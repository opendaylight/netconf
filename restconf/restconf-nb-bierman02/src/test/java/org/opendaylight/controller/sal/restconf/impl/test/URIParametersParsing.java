/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.restconf.impl.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Preconditions;
import java.io.FileNotFoundException;
import java.util.Set;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.sal.restconf.impl.BrokerFacade;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfImpl;
import org.opendaylight.netconf.sal.streams.listeners.ListenerAdapter;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeAttrBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.util.SchemaNodeUtils;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;

public class URIParametersParsing {

    private RestconfImpl restconf;
    private BrokerFacade mockedBrokerFacade;
    private ControllerContext controllerContext;

    @Before
    public void init() throws FileNotFoundException, ReactorException {
        this.restconf = RestconfImpl.getInstance();
        this.mockedBrokerFacade = mock(BrokerFacade.class);
        this.controllerContext = ControllerContext.getInstance();
        this.controllerContext.setSchemas(TestUtils.loadSchemaContext("/datastore-and-scope-specification"));
        this.restconf.setControllerContext(this.controllerContext);
        this.restconf.setBroker(this.mockedBrokerFacade);
    }

    @Test
    public void resolveURIParametersConcreteValues() {
        resolveURIParameters("OPERATIONAL", "SUBTREE", LogicalDatastoreType.OPERATIONAL, DataChangeScope.SUBTREE);
    }

    @Test
    public void resolveURIParametersDefaultValues() {
        resolveURIParameters(null, null, LogicalDatastoreType.CONFIGURATION, DataChangeScope.BASE);
    }

    private void resolveURIParameters(final String datastore, final String scope,
            final LogicalDatastoreType datastoreExpected, final DataChangeScope scopeExpected) {

        final InstanceIdentifierBuilder iiBuilder = YangInstanceIdentifier.builder();
        iiBuilder.node(QName.create("", "dummyStreamName"));

        final String datastoreValue = datastore == null ? "CONFIGURATION" : datastore;
        final String scopeValue = scope == null ? "BASE" : scope + "";
        Notificator.createListener(iiBuilder.build(), "dummyStreamName/datastore=" + datastoreValue + "/scope="
                + scopeValue, NotificationOutputType.XML);

        final UriInfo mockedUriInfo = mock(UriInfo.class);
        @SuppressWarnings("unchecked")
        final MultivaluedMap<String, String> mockedMultivaluedMap = mock(MultivaluedMap.class);
        when(mockedMultivaluedMap.getFirst(eq("datastore"))).thenReturn(datastoreValue);
        when(mockedMultivaluedMap.getFirst(eq("scope"))).thenReturn(scopeValue);

        when(mockedUriInfo.getQueryParameters(eq(false))).thenReturn(mockedMultivaluedMap);

        final UriBuilder uriBuilder = UriBuilder.fromUri("www.whatever.com");
        when(mockedUriInfo.getAbsolutePathBuilder()).thenReturn(uriBuilder);

        this.restconf.invokeRpc("sal-remote:create-data-change-event-subscription",
            prepareDomRpcNode(datastoreValue, scopeValue), mockedUriInfo);

        final ListenerAdapter listener =
                Notificator.getListenerFor("data-change-event-subscription/opendaylight-inventory:nodes/datastore="
                + datastoreValue + "/scope=" + scopeValue);
        assertNotNull(listener);
    }

    private NormalizedNodeContext prepareDomRpcNode(final String datastore, final String scope) {
        final SchemaContext schema = this.controllerContext.getGlobalSchema();
        final Module rpcSalRemoteModule = schema.findModule("sal-remote", Revision.of("2014-01-14")).get();
        final Set<RpcDefinition> setRpcs = rpcSalRemoteModule.getRpcs();
        final QName rpcQName =
                QName.create(rpcSalRemoteModule.getQNameModule(), "create-data-change-event-subscription");
        final QName rpcInputQName =
                QName.create("urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote", "2014-01-14", "input");
        final RpcDefinition rpcDef = Mockito.mock(RpcDefinition.class);
        ContainerSchemaNode rpcInputSchemaNode = null;
        for (final RpcDefinition rpc : setRpcs) {
            if (rpcQName.isEqualWithoutRevision(rpc.getQName())) {
                rpcInputSchemaNode = SchemaNodeUtils.getRpcDataSchema(rpc, rpcInputQName);
                break;
            }
        }
        assertNotNull("RPC ContainerSchemaNode was not found!", rpcInputSchemaNode);

        final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> container =
                Builders.containerBuilder(rpcInputSchemaNode);

        final QName pathQName =
                QName.create("urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote", "2014-01-14", "path");
        final DataSchemaNode pathSchemaNode = rpcInputSchemaNode.getDataChildByName(pathQName);
        assertTrue(pathSchemaNode instanceof LeafSchemaNode);
        final LeafNode<Object> pathNode = Builders.leafBuilder((LeafSchemaNode) pathSchemaNode)
                .withValue(YangInstanceIdentifier.builder()
                        .node(QName.create("urn:opendaylight:inventory", "2013-08-19", "nodes")).build()).build();
        container.withChild(pathNode);

        final AugmentationSchemaNode augmentationSchema = rpcInputSchemaNode.getAvailableAugmentations().iterator()
                .next();
        Preconditions.checkNotNull(augmentationSchema);
        final DataContainerNodeBuilder<AugmentationIdentifier, AugmentationNode> augmentationBuilder =
                Builders.augmentationBuilder(augmentationSchema);

        final QName dataStoreQName = QName.create("urn:sal:restconf:event:subscription", "2014-07-08", "datastore");
        final DataSchemaNode dsSchemaNode = augmentationSchema.getDataChildByName(dataStoreQName);
        assertTrue(dsSchemaNode instanceof LeafSchemaNode);
        final LeafNode<Object> dsNode = Builders.leafBuilder((LeafSchemaNode) dsSchemaNode)
                .withValue(datastore).build();
        augmentationBuilder.withChild(dsNode);

        final QName scopeQName = QName.create("urn:sal:restconf:event:subscription", "2014-07-08", "scope");
        final DataSchemaNode scopeSchemaNode = augmentationSchema.getDataChildByName(scopeQName);
        assertTrue(scopeSchemaNode instanceof LeafSchemaNode);
        final LeafNode<Object> scopeNode = Builders.leafBuilder((LeafSchemaNode) scopeSchemaNode)
                .withValue(scope).build();
        augmentationBuilder.withChild(scopeNode);

        final QName outputQName =
                QName.create("urn:sal:restconf:event:subscription", "2014-07-08", "notification-output-type");
        final DataSchemaNode outputSchemaNode = augmentationSchema.getDataChildByName(outputQName);
        assertTrue(outputSchemaNode instanceof LeafSchemaNode);
        final LeafNode<Object> outputNode =
                Builders.leafBuilder((LeafSchemaNode) outputSchemaNode).withValue("XML").build();
        augmentationBuilder.withChild(outputNode);

        container.withChild(augmentationBuilder.build());

        when(rpcDef.getInput()).thenReturn(rpcInputSchemaNode);
        when(rpcDef.getPath()).thenReturn(SchemaPath.create(true, rpcQName));
        when(rpcDef.getQName()).thenReturn(rpcQName);

        return new NormalizedNodeContext(new InstanceIdentifierContext<>(null, rpcDef, null, schema),
                container.build());
    }
}
