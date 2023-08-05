/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.operations;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.util.Deque;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public final class DataTreeChange {
    private final NormalizedNode changeRoot;
    private final YangInstanceIdentifier path;
    private final EffectiveOperation action;

    DataTreeChange(final NormalizedNode changeRoot, final EffectiveOperation action, final Deque<PathArgument> path) {
        this.changeRoot = requireNonNull(changeRoot);
        this.action = requireNonNull(action);

        final Builder<PathArgument> builder = ImmutableList.builderWithExpectedSize(path.size());
        path.descendingIterator().forEachRemaining(builder::add);
        this.path = YangInstanceIdentifier.of(builder.build());
    }

    public NormalizedNode getChangeRoot() {
        return changeRoot;
    }

    public EffectiveOperation getAction() {
        return action;
    }

    public YangInstanceIdentifier getPath() {
        return path;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("action", action).add("path", path).add("root", changeRoot)
                .toString();
    }
}
