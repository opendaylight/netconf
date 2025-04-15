/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.MoreExecutors;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.OnCommitFutureCallback;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
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
    public void channelClosed() {
        final var id = id();
        LOG.debug("{} terminated after channel was closed", id);
        removeSubscription(id, null, null);
    }

    @Override
    protected void terminateImpl(final ServerRequest<Empty> request, final QName terminationReason) {
        final var id = id();
        LOG.debug("{} terminated with reason {}", id, terminationReason);
        removeSubscription(id, request, terminationReason);
    }

    private void removeSubscription(final Uint32 id, final ServerRequest<Empty> request,
            final QName terminationReason) {
        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.delete(LogicalDatastoreType.OPERATIONAL, SubscriptionUtil.SUBSCRIPTIONS.node(
            NodeIdentifierWithPredicates.of(Subscription.QNAME, SubscriptionUtil.QNAME_ID, id)));
        tx.commit().addCallback(new OnCommitFutureCallback() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("Removed subscription {} from operational datastore as of {}", id, result);
                if (request != null) {
                    delegate.terminate(request.transform(ignored -> Empty.value()), terminationReason);
                } else {
                    delegate.channelClosed();
                }
            }

            @Override
            public void onFailure(final TransactionCommitFailedException cause) {
                LOG.warn("Failed to remove subscription {} from operational datastore", id, cause);
                if (request != null) {
                    request.completeWith(new RequestException(cause));
                }
            }
        }, MoreExecutors.directExecutor());
    }
}
