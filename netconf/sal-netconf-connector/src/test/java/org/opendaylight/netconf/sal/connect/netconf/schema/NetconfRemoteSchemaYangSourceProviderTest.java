/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.schema;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import java.util.Collections;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.md.sal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableAnyXmlNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.repo.api.RevisionSourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class NetconfRemoteSchemaYangSourceProviderTest {

    @Mock
    private DOMRpcService service;

    private NetconfRemoteSchemaYangSourceProvider provider;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        final DOMRpcResult value = new DefaultDOMRpcResult(getNode(), Collections.<RpcError>emptySet());
        CheckedFuture<DOMRpcResult, DOMRpcException> response = Futures.immediateCheckedFuture(value);
        doReturn(response).when(service).invokeRpc(any(SchemaPath.class), any(NormalizedNode.class));

        provider = new NetconfRemoteSchemaYangSourceProvider(
                new RemoteDeviceId("device1", InetSocketAddress.createUnresolved("localhost", 17830)), service);
    }

    @Test
    public void testGetSource() throws Exception {
        final SourceIdentifier identifier = RevisionSourceIdentifier.create("test", Revision.of("2016-02-08"));
        final YangTextSchemaSource source = provider.getSource(identifier).get();
        Assert.assertEquals(identifier, source.getIdentifier());
        verify(service).invokeRpc(
                SchemaPath.create(true, NetconfMessageTransformUtil.GET_SCHEMA_QNAME),
                NetconfRemoteSchemaYangSourceProvider.createGetSchemaRequest(identifier.getName(),
                    Optional.fromJavaUtil(identifier.getRevision().map(Revision::toString)))
        );
    }

    private static NormalizedNode<?, ?> getNode() throws ParserConfigurationException {
        final YangInstanceIdentifier.NodeIdentifier id = YangInstanceIdentifier.NodeIdentifier.create(
                QName.create("urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring", "2010-10-04", "output")
        );
        final YangInstanceIdentifier.NodeIdentifier childId = YangInstanceIdentifier.NodeIdentifier.create(
                QName.create("urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring", "2010-10-04", "data")
        );
        Document xmlDoc = UntrustedXML.newDocumentBuilder().newDocument();
        Element root = xmlDoc.createElement("data");
        root.setTextContent("module test {}");
        final DOMSource v = new DOMSource(root);
        DataContainerChild<?, ?> child =
                ImmutableAnyXmlNodeBuilder.create().withNodeIdentifier(childId).withValue(v).build();
        return ImmutableContainerNodeBuilder.create().withNodeIdentifier(id).withChild(child).build();
    }

}