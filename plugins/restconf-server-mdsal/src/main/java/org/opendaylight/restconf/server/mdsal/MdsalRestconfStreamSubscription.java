/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.function.Consumer;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.ForwardingRestconfStreamSubscription;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.subscription.SubscriptionUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MdsalRestconfStreamSubscription<T extends RestconfStream.Subscription>
        extends ForwardingRestconfStreamSubscription<T> {
    private static final Logger LOG = LoggerFactory.getLogger(MdsalRestconfStreamSubscription.class);

    private final DOMDataBroker dataBroker;

    MdsalRestconfStreamSubscription(final T delegate, final DOMDataBroker dataBroker) {
        super(delegate);
        this.dataBroker = requireNonNull(dataBroker);
    }

    @Override
    @NonNullByDefault
    protected void terminateImpl(final ServerRequest<Empty> request, final QName terminationReason) {
        final var id = id();
        LOG.debug("{} terminated with reason {}", id, terminationReason);
        removeSubscription(id, () -> delegate.terminate(request.transform(ignored -> Empty.value()), terminationReason),
            cause -> request.completeWith(new RequestException(cause)));
    }

    @Override
    public void channelClosed() {
        final var id = id();
        LOG.debug("{} terminated after channel was closed", id);
        removeSubscription(id, delegate::channelClosed, null);
    }

    private void removeSubscription(final Uint32 id, final Runnable onSuccess, final Consumer<Throwable> onFailure) {
        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.delete(LogicalDatastoreType.OPERATIONAL, SubscriptionUtil.SUBSCRIPTIONS.node(
            NodeIdentifierWithPredicates.of(Subscription.QNAME, SubscriptionUtil.QNAME_ID, id)));
        tx.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("Removed subscription {} from operational datastore as of {}", id, result);
                onSuccess.run();
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.warn("Failed to remove subscription {} from operational datastore", id, cause);
                if (onFailure != null) {
                    onFailure.accept(cause);
                }
            }
        }, MoreExecutors.directExecutor());
    }
}
