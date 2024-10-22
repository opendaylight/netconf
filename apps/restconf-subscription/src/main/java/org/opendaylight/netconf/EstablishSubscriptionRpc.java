/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;

import java.net.URI;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionInput;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * RESTCONF implementation of {@link EstablishSubscription}.
 */
@Singleton
@Component(service = RpcImplementation.class)
public class EstablishSubscriptionRpc extends RpcImplementation {
    private static final NodeIdentifier DEVICE_NOTIFICATION_PATH_NODEID =
        NodeIdentifier.create(QName.create(EstablishSubscriptionInput.QNAME, "path").intern());
    // Start subscription ID generation from the upper half of uint32 (2,147,483,648)
    private static final long INITIAL_SUBSCRIPTION_ID = 2147483648L;

    private final AtomicLong subscriptionIdCounter = new AtomicLong(INITIAL_SUBSCRIPTION_ID);
    private final DOMDataBroker dataBroker;

    @Inject
    @Activate
    public EstablishSubscriptionRpc(@Reference final DOMDataBroker dataBroker) {
        super(EstablishSubscription.QNAME);
        this.dataBroker = requireNonNull(dataBroker);
    }

    @Override
    public void invoke(ServerRequest<ContainerNode> request, URI restconfURI, OperationInput input) {
        final var session = request.session();
        final var subscription = new Subscription(SubscriptionUtil.generateSubscriptionId(subscriptionIdCounter));

        session.registerResource(subscription);
    }
}
