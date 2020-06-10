/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public interface RestconfStrategy {
    void setLogicalDatastoreType(LogicalDatastoreType datastoreType);

    void prepareExecution();

    void close();

    void cancel();

    ListenableFuture<Optional<NormalizedNode<?, ?>>> read();

    FluentFuture<Boolean> exists(LogicalDatastoreType store, YangInstanceIdentifier path);

    void delete(LogicalDatastoreType store, YangInstanceIdentifier path);

    void merge(LogicalDatastoreType store, YangInstanceIdentifier path, NormalizedNode<?, ?> data);

    void create(LogicalDatastoreType store, YangInstanceIdentifier path, NormalizedNode<?, ?> data);

    void replace(LogicalDatastoreType store, YangInstanceIdentifier path, NormalizedNode<?, ?> data);

    FluentFuture<? extends @NonNull CommitInfo> commit();

    DOMTransactionChain getTransactionChain();

    InstanceIdentifierContext<?> getInstanceIdentifier();
}
