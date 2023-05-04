/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.FluentFuture;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class MonitoringSchemaSourceProviderTest {
    @Mock
    private DOMRpcService service;

    private MonitoringSchemaSourceProvider provider;

    @Before
    public void setUp() throws Exception {
        final DOMRpcResult value = new DefaultDOMRpcResult(getNode(), Set.of());
        final FluentFuture<DOMRpcResult> response = FluentFutures.immediateFluentFuture(value);
        doReturn(response).when(service).invokeRpc(any(QName.class), any(ContainerNode.class));

        provider = new MonitoringSchemaSourceProvider(
                new RemoteDeviceId("device1", InetSocketAddress.createUnresolved("localhost", 17830)), service);
    }

    @Test
    public void testGetSource() throws Exception {
        final SourceIdentifier identifier = new SourceIdentifier("test", "2016-02-08");
        final YangTextSchemaSource source = provider.getSource(identifier).get();
        assertEquals(identifier, source.getIdentifier());
        verify(service).invokeRpc(NetconfMessageTransformUtil.GET_SCHEMA_QNAME,
                MonitoringSchemaSourceProvider.createGetSchemaRequest("test", Optional.of("2016-02-08")));
    }

    private static ContainerNode getNode() throws ParserConfigurationException {
        final YangInstanceIdentifier.NodeIdentifier id = YangInstanceIdentifier.NodeIdentifier.create(
                QName.create("urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring", "2010-10-04", "output")
        );
        final YangInstanceIdentifier.NodeIdentifier childId = YangInstanceIdentifier.NodeIdentifier.create(
                QName.create("urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring", "2010-10-04", "data")
        );
        Document xmlDoc = UntrustedXML.newDocumentBuilder().newDocument();
        Element root = xmlDoc.createElement("data");
        root.setTextContent("module test {}");
        return Builders.containerBuilder()
            .withNodeIdentifier(id)
            .withChild(Builders.anyXmlBuilder()
                .withNodeIdentifier(childId)
                .withValue(new DOMSource(root))
                .build())
            .build();
    }
}
