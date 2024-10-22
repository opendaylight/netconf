/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf;

import java.net.URI;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.DeleteSubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.DeleteSubscriptionInput;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * RESTCONF implementation of {@link DeleteSubscription}.
 */
@Singleton
@Component(service = RpcImplementation.class)
public class DeleteSubscriptionRpc extends RpcImplementation {
    private static final YangInstanceIdentifier.NodeIdentifier DEVICE_NOTIFICATION_PATH_NODEID =
        YangInstanceIdentifier.NodeIdentifier.create(QName.create(DeleteSubscriptionInput.QNAME, "path").intern());

    private final SubscriptionTracker subscriptionTracker;

    @Inject
    @Activate
    public DeleteSubscriptionRpc(@Reference final SubscriptionTracker tracker) {
        super(DeleteSubscription.QNAME);
        subscriptionTracker = tracker;
    }

    @Override
    public void invoke(ServerRequest<ContainerNode> request, URI restconfURI, OperationInput input) {

    }
}
