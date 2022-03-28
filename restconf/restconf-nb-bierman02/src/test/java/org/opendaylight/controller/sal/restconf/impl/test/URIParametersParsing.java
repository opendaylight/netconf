/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.restconf.impl.test;

import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.sal.rest.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.BrokerFacade;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfImpl;
import org.opendaylight.netconf.sal.streams.listeners.ListenerAdapter;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.CreateDataChangeEventSubscriptionInput1.Scope;
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
import org.opendaylight.yangtools.yang.data.api.schema.builder.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.SchemaAwareBuilders;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerLike;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.InputSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;

public class URIParametersParsing {

    private RestconfImpl restconf;
    private BrokerFacade mockedBrokerFacade;
    private ControllerContext controllerContext;

    @Before
    public void init() throws FileNotFoundException, ReactorException {
        mockedBrokerFacade = mock(BrokerFacade.class);
        controllerContext = TestRestconfUtils.newControllerContext(
                TestUtils.loadSchemaContext("/datastore-and-scope-specification"));
        restconf = RestconfImpl.newInstance(mockedBrokerFacade, controllerContext);
    }

    @Test
    public void resolveURIParametersConcreteValues() {
        resolveURIParameters("OPERATIONAL", "SUBTREE", LogicalDatastoreType.OPERATIONAL, Scope.SUBTREE);
    }

    @Test
    public void resolveURIParametersDefaultValues() {
        resolveURIParameters(null, null, LogicalDatastoreType.CONFIGURATION, Scope.BASE);
    }

    private void resolveURIParameters(final String datastore, final String scope,
            final LogicalDatastoreType datastoreExpected, final Scope scopeExpected) {

        final InstanceIdentifierBuilder iiBuilder = YangInstanceIdentifier.builder();
        iiBuilder.node(QName.create("", "dummyStreamName"));

        final String datastoreValue = datastore == null ? "CONFIGURATION" : datastore;
        final String scopeValue = scope == null ? "BASE" : scope + "";
        Notificator.createListener(iiBuilder.build(), "dummyStreamName/datastore=" + datastoreValue + "/scope="
                + scopeValue, NotificationOutputType.XML, controllerContext);

        final UriInfo mockedUriInfo = mock(UriInfo.class);
        @SuppressWarnings("unchecked")
        final MultivaluedMap<String, String> mockedMultivaluedMap = mock(MultivaluedMap.class);
        when(mockedMultivaluedMap.getFirst(eq("datastore"))).thenReturn(datastoreValue);
        when(mockedMultivaluedMap.getFirst(eq("scope"))).thenReturn(scopeValue);

        when(mockedUriInfo.getQueryParameters(eq(false))).thenReturn(mockedMultivaluedMap);

        final UriBuilder uriBuilder = UriBuilder.fromUri("www.whatever.com");
        when(mockedUriInfo.getAbsolutePathBuilder()).thenReturn(uriBuilder);

        restconf.invokeRpc("sal-remote:create-data-change-event-subscription",
            prepareDomRpcNode(datastoreValue, scopeValue), mockedUriInfo);

        final ListenerAdapter listener =
                Notificator.getListenerFor("data-change-event-subscription/opendaylight-inventory:nodes/datastore="
                + datastoreValue + "/scope=" + scopeValue);
        assertNotNull(listener);
    }

    private NormalizedNodeContext prepareDomRpcNode(final String datastore, final String scope) {
        final EffectiveModelContext schema = controllerContext.getGlobalSchema();
        final Module rpcSalRemoteModule = schema.findModule("sal-remote", Revision.of("2014-01-14")).get();
        final QName rpcQName =
                QName.create(rpcSalRemoteModule.getQNameModule(), "create-data-change-event-subscription");
        final RpcDefinition rpcDef = Mockito.mock(RpcDefinition.class);
        ContainerLike rpcInputSchemaNode = null;
        for (final RpcDefinition rpc : rpcSalRemoteModule.getRpcs()) {
            if (rpcQName.isEqualWithoutRevision(rpc.getQName())) {
                rpcInputSchemaNode = rpc.getInput();
                break;
            }
        }
        assertNotNull("RPC ContainerSchemaNode was not found!", rpcInputSchemaNode);

        final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> container =
                SchemaAwareBuilders.containerBuilder(rpcInputSchemaNode);

        final QName pathQName =
                QName.create("urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote", "2014-01-14", "path");
        final DataSchemaNode pathSchemaNode = rpcInputSchemaNode.getDataChildByName(pathQName);
        assertTrue(pathSchemaNode instanceof LeafSchemaNode);
        final LeafNode<Object> pathNode = SchemaAwareBuilders.leafBuilder((LeafSchemaNode) pathSchemaNode)
                .withValue(YangInstanceIdentifier.builder()
                        .node(QName.create("urn:opendaylight:inventory", "2013-08-19", "nodes")).build()).build();
        container.withChild(pathNode);

        final AugmentationSchemaNode augmentationSchema = requireNonNull(rpcInputSchemaNode.getAvailableAugmentations()
                .iterator().next());
        final DataContainerNodeBuilder<AugmentationIdentifier, AugmentationNode> augmentationBuilder =
                SchemaAwareBuilders.augmentationBuilder(augmentationSchema);

        final QName dataStoreQName = QName.create("urn:sal:restconf:event:subscription", "2014-07-08", "datastore");
        final DataSchemaNode dsSchemaNode = augmentationSchema.getDataChildByName(dataStoreQName);
        assertTrue(dsSchemaNode instanceof LeafSchemaNode);
        final LeafNode<Object> dsNode = SchemaAwareBuilders.leafBuilder((LeafSchemaNode) dsSchemaNode)
                .withValue(datastore).build();
        augmentationBuilder.withChild(dsNode);

        final QName scopeQName = QName.create("urn:sal:restconf:event:subscription", "2014-07-08", "scope");
        final DataSchemaNode scopeSchemaNode = augmentationSchema.getDataChildByName(scopeQName);
        assertTrue(scopeSchemaNode instanceof LeafSchemaNode);
        final LeafNode<Object> scopeNode = SchemaAwareBuilders.leafBuilder((LeafSchemaNode) scopeSchemaNode)
                .withValue(scope).build();
        augmentationBuilder.withChild(scopeNode);

        final QName outputQName =
                QName.create("urn:sal:restconf:event:subscription", "2014-07-08", "notification-output-type");
        final DataSchemaNode outputSchemaNode = augmentationSchema.getDataChildByName(outputQName);
        assertTrue(outputSchemaNode instanceof LeafSchemaNode);
        final LeafNode<Object> outputNode =
            SchemaAwareBuilders.leafBuilder((LeafSchemaNode) outputSchemaNode).withValue("XML").build();
        augmentationBuilder.withChild(outputNode);

        container.withChild(augmentationBuilder.build());

        when(rpcDef.getInput()).thenReturn((InputSchemaNode) rpcInputSchemaNode);
        when(rpcDef.getQName()).thenReturn(rpcQName);

        return new NormalizedNodeContext(InstanceIdentifierContext.ofRpcInput(schema, rpcDef, null), container.build());
    }
}
