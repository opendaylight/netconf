/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf;

import static org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;

import java.net.URI;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.ModifySubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.ModifySubscriptionInput;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * RESTCONF implementation of {@link ModifySubscription}.
 */
@Singleton
@Component(service = RpcImplementation.class)
public class ModifySubscriptionRpc extends RpcImplementation {
    private static final NodeIdentifier DEVICE_NOTIFICATION_PATH_NODEID =
        NodeIdentifier.create(QName.create(ModifySubscriptionInput.QNAME, "id").intern());

    private final SubscriptionTracker subscriptionTracker;

    @Inject
    @Activate
    public ModifySubscriptionRpc(@Reference final SubscriptionTracker tracker) {
        super(EstablishSubscription.QNAME);
        subscriptionTracker = tracker;
    }

    @Override
    public void invoke(ServerRequest<ContainerNode> request, URI restconfURI, OperationInput input) {

    }
}
