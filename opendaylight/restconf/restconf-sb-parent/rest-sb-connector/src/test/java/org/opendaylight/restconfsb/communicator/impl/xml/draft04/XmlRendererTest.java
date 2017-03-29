/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.impl.xml.draft04;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.restconfsb.communicator.api.http.Request;
import org.opendaylight.restconfsb.communicator.impl.xml.RetestUtils;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class XmlRendererTest {

    private XmlRenderer renderer;

    public static final String NS = "urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring";
    public static final String REV = "2010-10-04";

    @Before
    public void setUp() throws Exception {
        final List<InputStream> streams = new ArrayList<>();
        streams.add(getClass().getResourceAsStream("/yang/ietf-netconf-monitoring@2010-10-04.yang"));
        streams.add(getClass().getResourceAsStream("/yang/ietf-yang-types@2010-09-24.yang"));
        streams.add(getClass().getResourceAsStream("/yang/ietf-inet-types@2010-09-24.yang"));
        streams.add(getClass().getResourceAsStream("/yang/module-0@2016-03-01.yang"));

        final SchemaContext schemaContext = RetestUtils.parseYangStreams(streams);
        renderer = new XmlRenderer(schemaContext);
    }

    @Test
    public void testRenderUrlSuffix() throws Exception {
        final QName netconfState = QName.create(NS, REV, "netconf-state");
        final Map<QName, Object> keys = new HashMap<>();

        keys.put( QName.create(netconfState, "identifier"), "opendaylight-sal-dom-broker-impl");
        keys.put( QName.create(netconfState, "version"), "2013-10-28");
        keys.put( QName.create(netconfState, "format"), "ietf-netconf-monitoring:yang");
        final YangInstanceIdentifier id = YangInstanceIdentifier.builder()
                .node(netconfState)
                .node(QName.create(netconfState, "schemas"))
                .node(QName.create(netconfState, "schema"))
                .nodeWithKey(QName.create(netconfState, "schema"), keys)
                .node(QName.create(netconfState, "identifier"))
                .build();
        final Request r = renderer.renderGetData(id, LogicalDatastoreType.OPERATIONAL);
        Assert.assertEquals(
                "/data/ietf-netconf-monitoring:netconf-state/schemas/schema=opendaylight-sal-dom-broker-impl,2013-10-28,ietf-netconf-monitoring:yang/identifier?content=nonconfig",
                r.getPath());
        Assert.assertEquals("", r.getBody());
        Assert.assertEquals(Request.RestconfMediaType.XML_DATA, r.getType());
    }

    @Test
    public void testRenderUrlSuffixForChoice() throws Exception {
        final QName root = QName.create("urn:dummy:mod", "2016-03-01", "root");
        final YangInstanceIdentifier id = YangInstanceIdentifier.builder()
                .node(root)
                .node(QName.create(root, "ch"))
                .node(QName.create(root, "container-case"))
                .build();
        final Request r = renderer.renderGetData(id, LogicalDatastoreType.CONFIGURATION);
        Assert.assertEquals("/data/module-0:root/container-case?content=config", r.getPath());
        Assert.assertEquals("", r.getBody());
        Assert.assertEquals(Request.RestconfMediaType.XML_DATA, r.getType());
    }

    @Test
    public void testRenderRequestWithBodyChoice() throws Exception {
        final QName root = QName.create("urn:dummy:mod", "2016-03-01", "root");
        final YangInstanceIdentifier id = YangInstanceIdentifier.builder()
                .node(root)
                .node(QName.create(root, "ch"))
                .node(QName.create(root, "container-case"))
                .build();
        final LeafNode<Object> bLeaf = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(root, "b-leaf")))
                .withValue("bbbbbb")
                .build();
        final ContainerNode containerCase = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(root, "container-case")))
                .withChild(bLeaf)
                .build();
        final Request request = renderer.renderEditConfig(id, containerCase);
        Assert.assertEquals("/data/module-0:root/container-case?content=config", request.getPath());
//        Assert.assertEquals("<container-case xmlns=\"urn:dummy:mod-0\"><b-leaf>bbbbbb</b-leaf></container-case>", request.getBody());
        Assert.assertEquals(Request.RestconfMediaType.XML_DATA, request.getType());
    }

    @Test
    public void testRenderRequestWithBody() throws Exception {
        final QName netconfState = QName.create(NS, REV, "netconf-state");
        final QName schemas = QName.create(netconfState, "schemas");
        final QName schema = QName.create(netconfState, "schema");
        final Map<QName, Object> keys = new HashMap<>();

        keys.put( QName.create(netconfState, "identifier"), "opendaylight-sal-dom-broker-impl");
        keys.put( QName.create(netconfState, "version"), "2013-10-28");
        keys.put( QName.create(netconfState, "format"), "ietf-netconf-monitoring:yang");
        final List<DataContainerChild<? extends YangInstanceIdentifier.PathArgument, ?>> content = new ArrayList<>();

        final YangInstanceIdentifier id = YangInstanceIdentifier.builder()
                .node(netconfState)
                .node(schemas)
                .node(schema)
                .nodeWithKey(schema, keys)
                .build();

        content.add(
                Builders.leafBuilder()
                        .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(netconfState, "identifier")))
                        .withValue("opendaylight-sal-dom-broker-impl")
                        .build()
        );
        content.add(
                Builders.leafBuilder()
                        .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(netconfState, "version")))
                        .withValue("2013-10-28")
                        .build()
        );
        content.add(
                Builders.leafBuilder()
                        .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(netconfState, "format")))
                        .withValue("ietf-netconf-monitoring:yang")
                        .build()
        );
        content.add(
                Builders.leafBuilder()
                        .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(netconfState, "namespace")))
                        .withValue("urn:opendaylight:params:xml:ns:yang:controller:md:sal:dom:impl")
                        .build()
        );
        final MapEntryNode entry = Builders.mapEntryBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifierWithPredicates(schema, keys))
                .withValue(content)
                .build();
        final Request request = renderer.renderEditConfig(id, entry);
        Assert.assertEquals("/data/ietf-netconf-monitoring:netconf-state/schemas/schema=opendaylight-sal-dom-broker-impl,2013-10-28,ietf-netconf-monitoring:yang?content=config", request.getPath());
//        Assert.assertEquals("<schema xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\"><identifier>opendaylight-sal-dom-broker-impl</identifier><format>ietf-netconf-monitoring:yang</format><version>2013-10-28</version><namespace>urn:opendaylight:params:xml:ns:yang:controller:md:sal:dom:impl</namespace></schema>", request.getBody());
        Assert.assertEquals(Request.RestconfMediaType.XML_DATA, request.getType());
    }


}