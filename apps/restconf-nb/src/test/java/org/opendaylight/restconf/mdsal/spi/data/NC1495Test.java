/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.mdsal.spi.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.doReturn;
import static org.opendaylight.yangtools.util.concurrent.FluentFutures.immediateFluentFuture;
import static org.opendaylight.yangtools.yang.test.util.YangParserTestUtils.parseYang;

import com.google.common.util.concurrent.Futures;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.netconf.client.mdsal.spi.NetconfDataOperations;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.server.api.DataGetParams;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.UserMapNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

@ExtendWith(MockitoExtension.class)
class NC1495Test {
    private static final DatabindContext DATABIND = DatabindContext.ofModel(parseYang("""
        module userOrderList {
          namespace "urn:userOrderList";
          prefix uol;

          container container {
            list orderedList {
              ordered-by user;
              key key;
              leaf key {
                type string;
              }
            }
          }
        }"""));
    private static final Data ROOT = new Data(DATABIND);
    private static final QName MODULE_QNAME = QName.create("urn:userOrderList", "userOrderList");
    private static final QName CONTAINER_QNAME = QName.create(MODULE_QNAME, "container");
    private static final QName LIST_QNAME = QName.create(CONTAINER_QNAME, "orderedList");
    private static final QName KEY_QNAME = QName.create(LIST_QNAME, "key");
    private static final UserMapNode LIST = ImmutableNodes.newUserMapBuilder()
        .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
        .withChild(ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(LIST_QNAME, KEY_QNAME, "A"))
            .withChild(ImmutableNodes.leafNode(KEY_QNAME, "A"))
            .build())
        .withChild(ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(LIST_QNAME, KEY_QNAME, "B"))
            .withChild(ImmutableNodes.leafNode(KEY_QNAME, "B"))
            .build())
        .withChild(ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(LIST_QNAME, KEY_QNAME, "C"))
            .withChild(ImmutableNodes.leafNode(KEY_QNAME, "C"))
            .build())
        .withChild(ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(LIST_QNAME, KEY_QNAME, "D"))
            .withChild(ImmutableNodes.leafNode(KEY_QNAME, "D"))
            .build())
        .withChild(ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(LIST_QNAME, KEY_QNAME, "E"))
            .withChild(ImmutableNodes.leafNode(KEY_QNAME, "E"))
            .build())
        .build();
    private static final NormalizedNode CONTAINER = ImmutableNodes.newContainerBuilder()
        .withNodeIdentifier(NodeIdentifier.create(CONTAINER_QNAME))
        .withChild(LIST)
        .build();
    private static final YangInstanceIdentifier CONTAINER_INSTANCE = YangInstanceIdentifier.of(CONTAINER_QNAME);
    private static final Data CONTAINER_PATH = ROOT.enterPath(CONTAINER_INSTANCE);
    @Mock
    private NetconfDataTreeService netconfService;
    @Mock
    private DOMDataBroker dataBroker;
    @Mock
    private DOMDataTreeReadTransaction readTransaction;

    @Test
    void testListOrderInNetconfRestconfStrategy() throws Exception {
        final var dataOperations = new NetconfDataOperations(netconfService);
        doReturn(Futures.immediateFuture(Optional.of(CONTAINER))).when(netconfService).getConfig(CONTAINER_INSTANCE);
        doReturn(Futures.immediateFuture(Optional.of(CONTAINER))).when(netconfService).get(CONTAINER_INSTANCE);
        final var optionalNode = dataOperations.readData(CONTAINER_PATH,
                DataGetParams.of(QueryParameters.of(ContentParam.ALL))).get(2, TimeUnit.SECONDS);
        final var normalizedNode = optionalNode.orElseThrow();

        final var containerNode = assertInstanceOf(ContainerNode.class, normalizedNode);
        assertEquals(1, containerNode.body().size());
        assertEquals(LIST, containerNode.body().iterator().next());
    }

    @Test
    void testListOrderInMdsalRestconfStrategy() throws Exception {
        final var restconfStrategy = new MdsalRestconfStrategy(DATABIND, dataBroker);
        doReturn(readTransaction).when(dataBroker).newReadOnlyTransaction();
        doReturn(immediateFluentFuture(Optional.of(CONTAINER))).when(readTransaction).read(
            LogicalDatastoreType.CONFIGURATION, CONTAINER_INSTANCE);
        doReturn(immediateFluentFuture(Optional.of(CONTAINER))).when(readTransaction).read(
            LogicalDatastoreType.OPERATIONAL, CONTAINER_INSTANCE);
        final var optionalNode = restconfStrategy.readData(ContentParam.ALL, CONTAINER_PATH, null)
            .get(2, TimeUnit.SECONDS);
        final var normalizedNode = optionalNode.orElseThrow();

        final var containerNode = assertInstanceOf(ContainerNode.class, normalizedNode);
        assertEquals(1, containerNode.body().size());
        assertEquals(LIST, containerNode.body().iterator().next());
    }
}
