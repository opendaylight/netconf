/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.MoreExecutors;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.restconf.server.spi.AbstractRestconfStreamRegistry.SubscriptionControl;
import org.opendaylight.yangtools.yang.common.Uint32;

/**
 * A {@link SubscriptionControl} updating the MD-SAL datastore.
 */
@NonNullByDefault
final class MdsalSubscriptionControl implements SubscriptionControl {
    private final DOMDataBroker dataBroker;
    private final Uint32 subscriptionId;

    MdsalSubscriptionControl(final DOMDataBroker dataBroker, final Uint32 subscriptionId) {
        this.dataBroker = requireNonNull(dataBroker);
        this.subscriptionId = requireNonNull(subscriptionId);
    }

    @Override
    public FluentFuture<@Nullable Void> terminate() {
        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.delete(LogicalDatastoreType.OPERATIONAL, MdsalRestconfStreamRegistry.subscriptionPath(subscriptionId));
        return mapFuture(tx.commit());
    }

    private static FluentFuture<@Nullable Void> mapFuture(final FluentFuture<? extends CommitInfo> future) {
        return future.transform(unused -> null, MoreExecutors.directExecutor());
    }
}
