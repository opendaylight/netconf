/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.spi;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.distributed.tx.api.DTxException;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * DTX ReadWriteTransaction interface. It reuses ReadWriteTransaction.
 * The interface is implemented by DTX in CachingReadWriteTx.java.
 * Asynchronous methods in the interface have to be implemented.
 */
public interface  DTXReadWriteTransaction extends  ReadWriteTransaction{
    /**
     * Asynchronous put API and required by DTX.
     * This function invokes put() of a specific provider.
     *
     * @param <T> Class extends DataObject
     * @param logicalDatastoreType datastore type
     * @param instanceIdentifier IID for data
     * @param t data to put to the node
     *
     * @return CheckedFuture indicating the result of put.
     * <ul>
     * <li> Exceptions should be set to the future. </li>
     * </ul>
     */
    <T extends DataObject> CheckedFuture<Void, DTxException> asyncPut(final LogicalDatastoreType logicalDatastoreType,
                                                                      final InstanceIdentifier<T> instanceIdentifier, final T t) ;
    /**
     * Asynchronous merge API and required by DTX.
     * This function invokes merge() of a specific provider.
     *
     * @param <T> Class extends DataObject
     * @param logicalDatastoreType datastore type
     * @param instanceIdentifier IID for data
     * @param t data to put to the node
     *
     * @return CheckedFuture indicating the result of merge.
     * <ul>
     * <li> Exceptions should be set to the future. </li>
     * </ul>
     */
    <T extends DataObject> CheckedFuture<Void, DTxException>asyncMerge(final LogicalDatastoreType logicalDatastoreType,
                                                                                     final InstanceIdentifier<T> instanceIdentifier, final T t) ;
    /**
     * Asynchronous delete API and required by DTX.
     * This function invokes delete() of a specific provider.
     *
     * @param logicalDatastoreType datastore type
     * @param instanceIdentifier IID for data
     *
     * @return CheckedFuture indicating the result of delete.
     * <ul>
     * <li> Exceptions should be set to the future. </li>
     * </ul>
     */
    CheckedFuture<Void, DTxException> asyncDelete(final LogicalDatastoreType logicalDatastoreType,
                                                                final InstanceIdentifier<?> instanceIdentifier) ;

    /**
     * Inherited from ReadWriteTransaction. Deprecated in DTX.
     *
     * @param logicalDatastoreType datastore type
     * @param instanceIdentifier IID for data
     *
     */
    @Override void delete(final LogicalDatastoreType logicalDatastoreType,
                                 final InstanceIdentifier<?> instanceIdentifier) ;

    /**
     * Inherited from ReadWriteTransaction. Deprecated in DTX.
     *
     * @param <T> Class extends DataObject
     * @param logicalDatastoreType datastore type
     * @param instanceIdentifier IID for data
     * @param t data to delete from the node
     *
     */
    @Override <T extends DataObject> void merge(final LogicalDatastoreType logicalDatastoreType,
                                                       final InstanceIdentifier<T> instanceIdentifier, final T t, final boolean b) ;
    /**
     * Inherited from ReadWriteTransaction. Deprecated in DTX.
     *
     * @param <T> Class extends DataObject
     * @param logicalDatastoreType datastore type
     * @param instanceIdentifier IID for data
     * @param t data to merge to the node
     *
     */
    @Override <T extends DataObject> void put(final LogicalDatastoreType logicalDatastoreType,
                                                     final InstanceIdentifier<T> instanceIdentifier, final T t, final boolean ensureParents) ;

    /**
     * Inherited from ReadWriteTransaction and required by DTX.
     * This function invokes submit() to a specific provider.
     *
     * @return CheckedFuture indicating the result of submit.
     * <ul>
     * <li> Exceptions should be set to the future. </li>
     * </ul>
     */
    @Override CheckedFuture<Void, TransactionCommitFailedException> submit() ;

    /**
     * Inherited from ReadWriteTransaction and required by DTX.
     * This function invokes read() from a specific provider.
     *
     * @return CheckedFuture with the data and indicating the result of read.
     * <ul>
     * <li> Exceptions should be set to the future. </li>
     * </ul>
     */
    @Override public <T extends DataObject> CheckedFuture<Optional<T>, ReadFailedException> read(
            final LogicalDatastoreType logicalDatastoreType, final InstanceIdentifier<T> instanceIdentifier) ;
}
