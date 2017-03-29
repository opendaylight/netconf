/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.impl.xml.draft04;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.opendaylight.yangtools.yang.parser.spi.source.SourceException;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.CrossSourceStatementReactor;
import org.opendaylight.yangtools.yang.parser.stmt.rfc6020.YangInferencePipeline;
import org.opendaylight.yangtools.yang.parser.stmt.rfc6020.YangStatementSourceImpl;
import org.xml.sax.SAXException;

public class XmlParserTest {
    private static final QName NETWORK_TOPOLOGY = QName.create("urn:TBD:params:xml:ns:yang:network-topology", "2013-10-21", "network-topology");
    private static final QName TOPOLOGY = QName.create("urn:TBD:params:xml:ns:yang:network-topology", "2013-10-21", "topology");
    private static final QName NODE = QName.create("urn:TBD:params:xml:ns:yang:network-topology", "2013-10-21", "node");
    private static final QName NODE_ID = QName.create("urn:TBD:params:xml:ns:yang:network-topology", "2013-10-21", "node-id");
    private static final QName SUB_NAMES = QName.create("urn:TBD:params:xml:ns:yang:network-topology", "2013-10-21", "sub-names");

    private static final YangInstanceIdentifier NETWORK_TOPOLOGY_MODULES =
            YangInstanceIdentifier.builder()
                    .node(NETWORK_TOPOLOGY)
                    .build();
    private static final YangInstanceIdentifier SUB_NAMES_MODULES =
            YangInstanceIdentifier.builder()
                    .node(NETWORK_TOPOLOGY)
                    .node(SUB_NAMES)
                    .build();

    private static final YangInstanceIdentifier TOPOLOGY_MODULES =
            YangInstanceIdentifier.builder()
                    .node(NETWORK_TOPOLOGY)
                    .node(TOPOLOGY)
                    .nodeWithKey(TOPOLOGY, QName.create(TOPOLOGY, "topology-id"), "topology-netconf")
                    .build();

    private static final YangInstanceIdentifier TOPOLOGY_NODE_MODULES =
            YangInstanceIdentifier.builder()
                    .node(NETWORK_TOPOLOGY)
                    .node(TOPOLOGY)
                    .nodeWithKey(TOPOLOGY, QName.create(TOPOLOGY, "topology-id"), "topology-netconf")
                    .node(NODE)
                    .nodeWithKey(NODE, QName.create(NODE, "node-id"), "new-netconf-device")
                    .build();

    private static final YangInstanceIdentifier TOPOLOGY_NODEID_MODULES =
            YangInstanceIdentifier.builder()
                    .node(NETWORK_TOPOLOGY)
                    .node(TOPOLOGY)
                    .nodeWithKey(TOPOLOGY, QName.create(TOPOLOGY, "topology-id"), "topology-netconf")
                    .node(NODE)
                    .nodeWithKey(NODE, QName.create(NODE, "node-id"), "new-netconf-device")
                    .node(NODE_ID)
                    .build();

    private XmlParser parser;

    private static SchemaContext parseYangSources(final Collection<InputStream> testFiles)
            throws SourceException, ReactorException, FileNotFoundException {
        final CrossSourceStatementReactor.BuildAction reactor = YangInferencePipeline.RFC6020_REACTOR
                .newBuild();
        for (final InputStream testFile : testFiles) {
            reactor.addSource(new YangStatementSourceImpl(testFile));
        }
        return reactor.buildEffective();
    }

    @Before
    public void setUp() throws Exception {
        final List<InputStream> sources = new ArrayList<>();

        sources.add(getClass().getResourceAsStream("/yang/network-topology@2013-10-21.yang"));
        sources.add(getClass().getResourceAsStream("/yang/ietf-inet-types@2010-09-24.yang"));
        sources.add(getClass().getResourceAsStream("/yang/rpc@2016-05-09.yang"));
        sources.add(getClass().getResourceAsStream("/yang/module-0@2016-03-01.yang"));
        sources.add(getClass().getResourceAsStream("/yang/module-1@2016-03-01.yang"));

        final SchemaContext schemaContext = parseYangSources(sources);
        parser = new XmlParser(schemaContext);
    }

    @Test
    public void parseTest() throws Exception {
        //container node
        InputStream stream = XmlParserTest.class.getResourceAsStream("/xml/topology1.xml");
        NormalizedNode<?, ?> nn = parser.parse(NETWORK_TOPOLOGY_MODULES, stream);
        Assert.assertEquals("(urn:TBD:params:xml:ns:yang:network-topology?revision=2013-10-21)network-topology", nn.getNodeType().toString());

        //list node
        stream = XmlParserTest.class.getResourceAsStream("/xml/topology2.xml");
        nn = parser.parse(TOPOLOGY_MODULES, stream);
        Assert.assertEquals("(urn:TBD:params:xml:ns:yang:network-topology?revision=2013-10-21)topology", nn.getNodeType().toString());

        //list node
        stream = XmlParserTest.class.getResourceAsStream("/xml/topology3.xml");
        nn = parser.parse(TOPOLOGY_NODE_MODULES, stream);
        Assert.assertEquals("(urn:TBD:params:xml:ns:yang:network-topology?revision=2013-10-21)node", nn.getNodeType().toString());

        //leaf node
        stream = XmlParserTest.class.getResourceAsStream("/xml/topology4.xml");
        nn = parser.parse(TOPOLOGY_NODEID_MODULES, stream);
        Assert.assertEquals("(urn:TBD:params:xml:ns:yang:network-topology?revision=2013-10-21)node-id", nn.getNodeType().toString());

        //leaf-list node
        stream = XmlParserTest.class.getResourceAsStream("/xml/topology5.xml");
        nn = parser.parse(SUB_NAMES_MODULES, stream);
        Assert.assertEquals("(urn:TBD:params:xml:ns:yang:network-topology?revision=2013-10-21)sub-names", nn.getNodeType().toString());

        //null body
        stream = new ByteArrayInputStream("".getBytes());
        nn = parser.parse(NETWORK_TOPOLOGY_MODULES, stream);
        Assert.assertNull(nn);
    }

    @Test
    public void parseRpcOutputTest() throws Exception {
        final SchemaPath path = SchemaPath.create(true, QName.create("urn:opendaylight:params:xml:ns:yang:controller:md:sal:rpc", "2016-05-09", "method"));
        if (path == null) {
            throw new NullPointerException();
        }

        final InputStream stream = XmlParserTest.class.getResourceAsStream("/xml/rpc.xml");

        final NormalizedNode<?, ?> rpcOutput = parser.parseRpcOutput(path, stream);

        Assert.assertNotNull(rpcOutput);
        Assert.assertEquals("(urn:opendaylight:params:xml:ns:yang:controller:md:sal:rpc?revision=2016-05-09)output", rpcOutput.getNodeType().toString());
    }

    @Test
    public void parseRpcOutputEmptyTest() throws Exception {
        final SchemaPath path = SchemaPath.create(true, QName.create("urn:opendaylight:params:xml:ns:yang:controller:md:sal:rpc", "2016-05-09", "method2"));
        if (path == null) {
            throw new NullPointerException();
        }

        final InputStream stream = XmlParserTest.class.getResourceAsStream("/xml/rpc2.xml");

        final NormalizedNode<?, ?> rpcOutput = parser.parseRpcOutput(path, stream);

        Assert.assertNull(rpcOutput);
    }

    @Test(expected = IllegalStateException.class)
    public void parseRpcWrongXMLTest() throws Exception {
        SchemaPath path = SchemaPath.create(true, QName.create("urn:opendaylight:params:xml:ns:yang:controller:md:sal:rpc", "2016-05-09", "method"));
        if (path == null) {
            throw new NullPointerException();
        }
        Exception exc = null;
        final InputStream stream = new ByteArrayInputStream("<param1>10</param1> <param2>10</param2>".getBytes());
        try {
            parser.parseRpcOutput(path, stream);
        } catch (final Exception e) {
            Assert.assertTrue(e.getCause() instanceof SAXException);
            exc = e;
        }
        path = SchemaPath.create(true, QName.create("urn:opendaylight:params:xml:ns:yang:controller:md:sal:rpc", "2016-05-09", "method3"));
        try {
            parser.parseRpcOutput(path, stream);
        } catch (final Exception e) {
            Assert.assertTrue(e instanceof IllegalStateException);
            Assert.assertSame(e.getClass(), exc.getClass());
            throw e;
        }
    }

    @Test
    public void testParseNotification() throws Exception {
        final java.util.Scanner s = new java.util.Scanner(XmlParserTest.class.getResourceAsStream("/xml/notification.xml")).useDelimiter("\\A");
        final DOMNotification notification = parser.parseNotification(s.next());
        Assert.assertEquals(SchemaPath.create(true, QName.create("urn:dummy:mod", "2016-03-01", "not1")),
                notification.getType());
    }

    @Test(expected = IllegalStateException.class)
    public void testParseNotificationNoModule() throws Exception {
        final java.util.Scanner s = new java.util.Scanner(XmlParserTest.class.getResourceAsStream("/xml/notification2.xml")).useDelimiter("\\A");
        parser.parseNotification(s.next());
    }

}
