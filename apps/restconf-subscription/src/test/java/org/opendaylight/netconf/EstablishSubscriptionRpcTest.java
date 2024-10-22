/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.restconf.notifications.mdsal.MdsalNotificationService;
import org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService;
import org.opendaylight.restconf.server.api.DatabindPath;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.api.testlib.CompletingServerRequest;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

@ExtendWith(MockitoExtension.class)
public class EstablishSubscriptionRpcTest {
    private static final URI RESTCONF_URI = URI.create("/restconf/");

    @Mock
    private DOMDataBroker dataBroker;
    @Mock
    private SubscriptionStateService SubscriptionStateService;
    @Mock
    private DatabindPath.Rpc operationPath;
    @Mock
    private DOMDataTreeWriteTransaction writeTx;
    @Mock
    private DOMDataTreeReadTransaction readTx;
    @Mock
    private CompletingServerRequest<ContainerNode> request;
    @Mock
    private TransportSession session;
    @Captor
    private ArgumentCaptor<NormalizedNode> node;


    @Test
    void establishSubscriptionTest() throws Exception {
        final var mdsalService = new MdsalNotificationService(dataBroker);
        final var rpc = new EstablishSubscriptionRpc(mdsalService, SubscriptionStateService);

        final var builder = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(EstablishSubscriptionInput.QNAME))
            .withChild(ImmutableNodes.newContainerBuilder().withNodeIdentifier(NodeIdentifier
                    .create(QName.create(Subscription.QNAME, "target")))
                .withChild((ImmutableNodes.leafNode(SubscriptionUtil.QNAME_STREAM, "NETCONF")))
                .build());

        doReturn(writeTx).when(dataBroker).newWriteOnlyTransaction();
        doReturn(readTx).when(dataBroker).newReadOnlyTransaction();
        doReturn(FluentFutures.immediateTrueFluentFuture()).when(readTx).exists(LogicalDatastoreType.OPERATIONAL,
            SubscriptionUtil.STREAMS.node(NodeIdentifierWithPredicates.of(Stream.QNAME,
            SubscriptionUtil.QNAME_STREAM_NAME, "NETCONF")));
        doReturn(CommitInfo.emptyFluentFuture()).when(writeTx).commit();
        doReturn(session).when(request).session();

        rpc.invoke(request, RESTCONF_URI, new OperationInput(operationPath, builder.build()));
        verify(writeTx).put(eq(LogicalDatastoreType.OPERATIONAL), any(YangInstanceIdentifier.class), node.capture());

        //(SUBSCRIPTIONS.node(NodeIdentifierWithPredicates
        //            .of(Subscription.QNAME, SubscriptionUtil.QNAME_ID, 2147483648L)))
        assertNotNull(node.getValue());
    }
}
