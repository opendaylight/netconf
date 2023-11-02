/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

/**
 * A {@link Stream} for delivering a series of datastore subtree changes.
 */
public final class DataTreeChangeStream extends Stream {
    private final @NonNull LogicalDatastoreType datastore;
    private final @NonNull YangInstanceIdentifier path;

    DataTreeChangeStream(final String name, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path) {
        super(name);
        this.datastore = requireNonNull(datastore);
        this.path = requireNonNull(path);
    }

    @NonNull LogicalDatastoreType datastore() {
        return datastore;
    }

    @NonNull YangInstanceIdentifier path() {
        return path;
    }

    @Override
    ToStringHelper addToStringArguments(final ToStringHelper helper) {
        return super.addToStringArguments(helper).add("datastore", datastore).add("path", path);
    }
}
