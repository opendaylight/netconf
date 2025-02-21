/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.notifications.mdsal;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FluentFuture;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Singleton
@Component(service = MdsalNotificationService.class)
public class MdsalNotificationService {
    private final DOMDataBroker dataBroker;

    @Inject
    @Activate
    public MdsalNotificationService(@Reference final DOMDataBroker dataBroker) {
        this.dataBroker = requireNonNull(dataBroker);
    }

    public FluentFuture<Optional<NormalizedNode>> read(final YangInstanceIdentifier path) {
        try (var tx = dataBroker.newReadOnlyTransaction()) {
            return tx.read(LogicalDatastoreType.OPERATIONAL, path);
        }
    }

    public FluentFuture<Boolean> exist(final YangInstanceIdentifier path) {
        try (var tx = dataBroker.newReadOnlyTransaction()) {
            return tx.exists(LogicalDatastoreType.OPERATIONAL, path);
        }
    }

    public FluentFuture<? extends @NonNull CommitInfo> mergeSubscription(final YangInstanceIdentifier path,
            final NormalizedNode data) {
        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.merge(LogicalDatastoreType.OPERATIONAL, path, data);
        return tx.commit();
    }

    public FluentFuture<? extends @NonNull CommitInfo> deleteSubscription(final YangInstanceIdentifier path) {
        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.delete(LogicalDatastoreType.OPERATIONAL, path);
        return tx.commit();
    }
}
