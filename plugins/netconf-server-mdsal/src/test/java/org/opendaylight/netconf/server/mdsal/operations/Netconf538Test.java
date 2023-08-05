/*
 * Copyright (c) 2019 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.operations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import org.junit.Test;
import org.mockito.Mock;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.server.mdsal.CurrentSchemaContext;
import org.opendaylight.netconf.server.mdsal.TransactionProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class Netconf538Test {
    private static final SessionIdType SESSION_ID_FOR_REPORTING = new SessionIdType(Uint32.valueOf(123));
    private static final QName BASE = QName.create("urn:dummy:list", "2019-08-21", "user");
    private static final QName NAME_QNAME = QName.create(BASE, "name");
    private static final String LEAF_VALUE = "testName";

    @Mock
    private TransactionProvider transactionProvider;

    @Test
    public void testRootMap() throws Exception {
        final var context = YangParserTestUtils.parseYangResources(Netconf538Test.class, "/yang/simple-list.yang");
        final CurrentSchemaContext currentContext = mock(CurrentSchemaContext.class);
        doReturn(context).when(currentContext).getCurrentContext();

        final GetConfig getConfig = new GetConfig(SESSION_ID_FOR_REPORTING, currentContext, transactionProvider);

        final Document document = XmlUtil.readXmlToDocument(FilterContentValidatorTest.class
                .getResourceAsStream("/filter/netconf538.xml"));

        LeafNode<String> leafNode = ImmutableNodes.leafNode(NAME_QNAME, LEAF_VALUE);

        MapNode data = Builders.mapBuilder()
            .withNodeIdentifier(NodeIdentifier.create(BASE))
            .withChild(Builders.mapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(BASE, NAME_QNAME,LEAF_VALUE))
                .withChild(leafNode)
                .build())
            .build();

        final Node node = getConfig.serializeNodeWithParentStructure(document, YangInstanceIdentifier.of(BASE), data);

        assertNotNull(node);
        Node nodeUser = node.getFirstChild();
        assertEquals(data.name().getNodeType().getLocalName(), nodeUser.getLocalName());
        Node nodeName = nodeUser.getFirstChild();
        assertEquals(leafNode.name().getNodeType().getLocalName(), nodeName.getLocalName());
        assertEquals(leafNode.body(), nodeName.getFirstChild().getNodeValue());
    }
}
