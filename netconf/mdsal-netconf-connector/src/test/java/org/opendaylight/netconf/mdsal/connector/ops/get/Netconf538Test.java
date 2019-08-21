/*
 * Copyright (c) 2019 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector.ops.get;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mock;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class Netconf538Test {

    private static final String SESSION_ID_FOR_REPORTING = "netconf-test-session1";
    private static final QName BASE = QName.create("urn:dummy:list", "2019-08-21", "user");
    private static final QName NAME_QNAME = QName.create(BASE, "name");
    private static final String LEAF_VALUE = "testName";

    @Mock
    private TransactionProvider transactionProvider;

    @Test
    public void transformNormalizedNodeTest_mapNodeTest() throws Exception {

        final SchemaContext context = YangParserTestUtils.parseYangResources(Netconf538Test.class,
                "/yang/simple-list.yang");
        final CurrentSchemaContext currentContext = mock(CurrentSchemaContext.class);
        doReturn(context).when(currentContext).getCurrentContext();

        final GetConfig getConfig = new GetConfig(SESSION_ID_FOR_REPORTING, currentContext, transactionProvider);

        final Document document = XmlUtil.readXmlToDocument(FilterContentValidatorTest.class
                .getResourceAsStream("/filter/netconf538.xml"));

        LeafNode<String> leafNode = Builders.<String>leafBuilder()
                .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(NAME_QNAME))
                .withValue(LEAF_VALUE).build();

        MapEntryNode mapEntryNode = Builders.mapEntryBuilder()
            .withNodeIdentifier(new YangInstanceIdentifier
                .NodeIdentifierWithPredicates(BASE, NAME_QNAME, LEAF_VALUE))
            .withChild(leafNode).build();

        MapNode data = Builders.mapBuilder().withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(BASE))
                .withChild(mapEntryNode).build();


        final YangInstanceIdentifier path = YangInstanceIdentifier.builder().build();

        final Node node = getConfig.transformNormalizedNode(document, data, path);

        Assert.assertNotNull(node);
        Node nodeUser = node.getFirstChild();
        Assert.assertEquals(data.getIdentifier().getNodeType().getLocalName(), nodeUser.getLocalName());
        Node nodeName = nodeUser.getFirstChild();
        Assert.assertEquals(leafNode.getIdentifier().getNodeType().getLocalName(), nodeName.getLocalName());
        Assert.assertEquals(leafNode.getValue(), nodeName.getFirstChild().getNodeValue());
    }

}
