/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.restconf.impl.test;

import java.io.FileNotFoundException;
import java.util.HashSet;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.netconf.sal.restconf.impl.BrokerFacade;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.PutResult;
import org.opendaylight.netconf.sal.restconf.impl.RestconfImpl;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

@RunWith(MockitoJUnitRunner.class)
public class RestPutConfigTest {

    private static SchemaContext schemaContext;
    private RestconfImpl restconfService;
    private ControllerContext controllerCx;

    @Mock
    private BrokerFacade brokerFacade;

    @BeforeClass
    public static void staticInit() throws FileNotFoundException {
        schemaContext = TestRestconfUtils.loadSchemaContext("/test-config-data/yang1/", null);
    }

    @Before
    public void init() {
        this.controllerCx = TestRestconfUtils.newControllerContext(schemaContext);
        this.restconfService = RestconfImpl.newInstance(brokerFacade, controllerCx);
    }

    @Test
    public void testPutConfigData() {
        final String identifier = "test-interface:interfaces/interface/key";
        final InstanceIdentifierContext<?> iiCx = this.controllerCx.toInstanceIdentifier(identifier);
        final MapEntryNode data = Mockito.mock(MapEntryNode.class);
        final QName qName = QName.create("urn:ietf:params:xml:ns:yang:test-interface", "2014-07-01", "interface");
        final QName qNameKey = QName.create("urn:ietf:params:xml:ns:yang:test-interface", "2014-07-01", "name");
        final NodeIdentifierWithPredicates identWithPredicates =
                new NodeIdentifierWithPredicates(qName, qNameKey, "key");
        Mockito.when(data.getNodeType()).thenReturn(qName);
        Mockito.when(data.getIdentifier()).thenReturn(identWithPredicates);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iiCx, data);

        mockingBrokerPut(iiCx.getInstanceIdentifier(), data);

        final UriInfo uriInfo = Mockito.mock(UriInfo.class);
        final MultivaluedMap<String, String> value = Mockito.mock(MultivaluedMap.class);
        Mockito.when(value.entrySet()).thenReturn(new HashSet<>());
        Mockito.when(uriInfo.getQueryParameters()).thenReturn(value);
        this.restconfService.updateConfigurationData(identifier, payload, uriInfo);
    }

    @Test
    public void testPutConfigDataCheckOnlyLastElement() {
        final String identifier = "test-interface:interfaces/interface/key/sub-interface/subkey";
        final InstanceIdentifierContext<?> iiCx = this.controllerCx.toInstanceIdentifier(identifier);
        final MapEntryNode data = Mockito.mock(MapEntryNode.class);
        final QName qName = QName.create("urn:ietf:params:xml:ns:yang:test-interface", "2014-07-01", "sub-interface");
        final QName qNameSubKey = QName.create("urn:ietf:params:xml:ns:yang:test-interface", "2014-07-01", "sub-name");
        final NodeIdentifierWithPredicates identWithPredicates =
                new NodeIdentifierWithPredicates(qName, qNameSubKey, "subkey");
        Mockito.when(data.getNodeType()).thenReturn(qName);
        Mockito.when(data.getIdentifier()).thenReturn(identWithPredicates);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iiCx, data);

        mockingBrokerPut(iiCx.getInstanceIdentifier(), data);

        final UriInfo uriInfo = Mockito.mock(UriInfo.class);
        final MultivaluedMap<String, String> value = Mockito.mock(MultivaluedMap.class);
        Mockito.when(value.entrySet()).thenReturn(new HashSet<>());
        Mockito.when(uriInfo.getQueryParameters()).thenReturn(value);
        this.restconfService.updateConfigurationData(identifier, payload, uriInfo);
    }

    @Test(expected = RestconfDocumentedException.class)
    public void testPutConfigDataMissingUriKey() {
        final String identifier = "test-interface:interfaces/interface";
        this.controllerCx.toInstanceIdentifier(identifier);
    }

    @Test(expected = RestconfDocumentedException.class)
    public void testPutConfigDataDiferentKey() {
        final String identifier = "test-interface:interfaces/interface/key";
        final InstanceIdentifierContext<?> iiCx = this.controllerCx.toInstanceIdentifier(identifier);
        final MapEntryNode data = Mockito.mock(MapEntryNode.class);
        final QName qName = QName.create("urn:ietf:params:xml:ns:yang:test-interface", "2014-07-01", "interface");
        final QName qNameKey = QName.create("urn:ietf:params:xml:ns:yang:test-interface", "2014-07-01", "name");
        final NodeIdentifierWithPredicates identWithPredicates =
                new NodeIdentifierWithPredicates(qName, qNameKey, "notSameKey");
        Mockito.when(data.getNodeType()).thenReturn(qName);
        Mockito.when(data.getIdentifier()).thenReturn(identWithPredicates);
        final NormalizedNodeContext payload = new NormalizedNodeContext(iiCx, data);

        mockingBrokerPut(iiCx.getInstanceIdentifier(), data);

        final UriInfo uriInfo = Mockito.mock(UriInfo.class);
        final MultivaluedMap<String, String> value = Mockito.mock(MultivaluedMap.class);
        Mockito.when(value.entrySet()).thenReturn(new HashSet<>());
        Mockito.when(uriInfo.getQueryParameters()).thenReturn(value);
        this.restconfService.updateConfigurationData(identifier, payload, uriInfo);
    }

    private void mockingBrokerPut(final YangInstanceIdentifier yii, final NormalizedNode<?, ?> data) {
        final PutResult result = Mockito.mock(PutResult.class);
        Mockito.when(this.brokerFacade.commitConfigurationDataPut(schemaContext, yii, data, null, null))
                .thenReturn(result);
        Mockito.doReturn(CommitInfo.emptyFluentFuture()).when(result).getFutureOfPutData();
        Mockito.when(result.getStatus()).thenReturn(Status.OK);
    }
}
