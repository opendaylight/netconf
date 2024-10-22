/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import java.net.URI;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.restconf.notification.mdsal.MdsalNotificationaService;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.DeleteSubscription;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.DeleteSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Subscriptions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * RESTCONF implementation of {@link DeleteSubscription}.
 */
@Singleton
@Component(service = RpcImplementation.class)
public class DeleteSubscriptionRpc extends RpcImplementation {
    private static final NodeIdentifier SUBSCRIPTION_ID =
        NodeIdentifier.create(QName.create(DeleteSubscriptionInput.QNAME, "id").intern());
    private static final YangInstanceIdentifier SUBSCRIPTIONS = YangInstanceIdentifier.of(
        NodeIdentifier.create(Subscriptions.QNAME),
        NodeIdentifier.create(Subscription.QNAME));
    private static final QName QNAME_ID = QName.create(Subscription.QNAME, "id").intern();

    private final MdsalNotificationaService mdsalService;

    @Inject
    @Activate
    public DeleteSubscriptionRpc(@Reference final MdsalNotificationaService mdsalService) {
        super(DeleteSubscription.QNAME);
        this.mdsalService = requireNonNull(mdsalService);
    }

    @Override
    public void invoke(ServerRequest<ContainerNode> request, URI restconfURI, OperationInput input) {
        final var body = input.input();
        final Uint32 id;
        try {
            id = leaf(body, SUBSCRIPTION_ID, Uint32.class);
        } catch (IllegalArgumentException e) {
            request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT, e));
            return;
        }
        if (id == null) {
            request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.MISSING_ELEMENT,
                "No id specified"));
            return;
        }
        // TODO:Dynamic subscriptions can only be deleted via
        //   this RPC using a transport session connecting to the subscriber
        mdsalService.deleteSubscription(SUBSCRIPTIONS.node(YangInstanceIdentifier
            .NodeIdentifierWithPredicates.of(Subscription.QNAME, QNAME_ID, id)))
            .addCallback(new FutureCallback<CommitInfo>() {
                @Override
                public void onSuccess(CommitInfo result) {
                    request.completeWith(ImmutableNodes.newContainerBuilder().build());
                }

                @Override
                public void onFailure(Throwable throwable) {
                    request.completeWith(new ServerException(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED,
                        throwable.getMessage()));
                }
            }, MoreExecutors.directExecutor());
    }
}
