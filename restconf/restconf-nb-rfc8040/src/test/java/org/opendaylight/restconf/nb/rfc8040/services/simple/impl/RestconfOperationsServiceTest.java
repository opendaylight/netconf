/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.services.simple.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import java.net.URI;
import java.util.Set;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class RestconfOperationsServiceTest {

    @Mock
    private DOMMountPointService domMountPointService;

    @Mock
    private UriInfo uriInfo;

    private SchemaContext schemaContext;
    private SchemaContextHandler schemaContextHandler;
    private DOMMountPointServiceHandler domMountPointServiceHandler;

    private Set<QName> listOfRpcsNames;

    @Before
    public void init() throws Exception {
        MockitoAnnotations.initMocks(this);
        this.schemaContext = YangParserTestUtils.parseYangSources(TestRestconfUtils.loadFiles("/modules"));

        final TransactionChainHandler txHandler = Mockito.mock(TransactionChainHandler.class);
        final DOMTransactionChain domTx = Mockito.mock(DOMTransactionChain.class);
        Mockito.when(txHandler.get()).thenReturn(domTx);
        final DOMDataWriteTransaction wTx = Mockito.mock(DOMDataWriteTransaction.class);
        Mockito.when(domTx.newWriteOnlyTransaction()).thenReturn(wTx);
        Mockito.when(wTx.submit()).thenReturn(Futures.immediateCheckedFuture(null));
        this.schemaContextHandler = new SchemaContextHandler(txHandler);
        this.schemaContextHandler.onGlobalContextUpdated(this.schemaContext);

        this.domMountPointServiceHandler = new DOMMountPointServiceHandler(this.domMountPointService);

        final QNameModule module1 = QNameModule.create(new URI("module:1"), null);
        final QNameModule module2 = QNameModule.create(new URI("module:2"), null);

        this.listOfRpcsNames = ImmutableSet.of(QName.create(module1, "dummy-rpc1-module1"),
                QName.create(module1, "dummy-rpc2-module1"), QName.create(module2, "dummy-rpc1-module2"),
                QName.create(module2, "dummy-rpc2-module2"));
    }

    @Test
    public void getOperationsTest() {
        final RestconfOperationsServiceImpl oper =
                new RestconfOperationsServiceImpl(this.schemaContextHandler, this.domMountPointServiceHandler);
        final NormalizedNodeContext operations = oper.getOperations(this.uriInfo);
        final ContainerNode data = (ContainerNode) operations.getData();
        assertEquals("urn:ietf:params:xml:ns:yang:ietf-restconf", data.getNodeType().getNamespace().toString());
        assertEquals("operations", data.getNodeType().getLocalName());

        assertEquals(4, data.getValue().size());

        for (final DataContainerChild<? extends PathArgument, ?> child : data.getValue()) {
            assertNull(child.getValue());

            final QName qname = child.getNodeType().withoutRevision();
            assertTrue(this.listOfRpcsNames.contains(qname));
        }
    }
}
