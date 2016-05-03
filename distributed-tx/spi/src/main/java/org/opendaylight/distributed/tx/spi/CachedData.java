/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.spi;

import com.google.common.base.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.ModifyAction;

/**
 * In memory cache data object.
 */
public final class CachedData {
    private final LogicalDatastoreType logicalDsType;
    private final InstanceIdentifier<?> id;
    private final DataObject data;
    private final ModifyAction operation;

    public CachedData(@Nonnull final LogicalDatastoreType datastoreType, @Nonnull final InstanceIdentifier<?> id,
        @Nullable final DataObject data, @Nonnull final ModifyAction operation) {
        this.logicalDsType = datastoreType;
        this.id = id;
        this.data = data;
        this.operation = operation;
    }

    /**
     * Get the data from cache.
     *
     * @return optional of the data object.
     */
    public Optional<DataObject> getData() {
        return Optional.fromNullable(data);
    }

    /**
     * Get IID of the cache data.
     *
     * @return instance identifier of the data object.
     */
    public InstanceIdentifier<?> getId() {
        return id;
    }

    /**
     * Get operation type on the data.
     *
     * @return operation type.
     */
    public ModifyAction getOperation() {
        return operation;
    }


    /**
     * Get logical data store type of the cache data.
     *
     * @return LogicalDataStoreType of the object.
     */
    public LogicalDatastoreType getDsType() {
        return logicalDsType;
    }
}
