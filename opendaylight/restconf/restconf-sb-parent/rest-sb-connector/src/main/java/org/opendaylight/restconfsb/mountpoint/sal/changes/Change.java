/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint.sal.changes;

import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.restconfsb.communicator.api.RestconfFacade;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * {@link org.opendaylight.restconfsb.mountpoint.sal.WriteOnlyTx} change. {@link AsyncFunction} is implemented to provide
 * change ordering.
 */
public abstract class Change implements AsyncFunction<Void, Void> {
    protected final RestconfFacade facade;
    private final YangInstanceIdentifier path;
    private final NormalizedNode<?, ?> normalizedNode;

    Change(final YangInstanceIdentifier path, final NormalizedNode<?, ?> normalizedNode, final RestconfFacade facade) {
        this.path = path;
        this.normalizedNode = normalizedNode;
        this.facade = facade;
    }

    /**
     * @return change content
     */
    public NormalizedNode<?, ?> getNormalizedNode() {
        return normalizedNode;
    }

    /**
     * @return change path
     */
    public YangInstanceIdentifier getPath() {
        return path;
    }

    @Override
    public abstract ListenableFuture<Void> apply(Void input);
}
