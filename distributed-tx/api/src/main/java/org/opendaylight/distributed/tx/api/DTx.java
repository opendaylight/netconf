/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.api;

import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Distributed transaction for multiple transaction providers and nodes.
 *
 * Reuse MD-SAL transaction API and add DTX specific API.
 */
public interface DTx extends WriteTransaction {
    /**
     * The method isn't implemented.
     */
    @Deprecated
    @Override void delete(LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<?> instanceIdentifier)
            throws DTxException.EditFailedException;

    /**
     * Delete data from a specific node. The function is deprecated in distributed-tx because of no return value.
     *
     * @param logicalDatastoreType datastore type
     * @param instanceIdentifier IID for data
     * @param nodeId IID for node to invoke delete
     *
     * @throws DTxException.EditFailedException thrown when delete fails, but rollback was successful
     * @throws DTxException.RollbackFailedException  thrown when delete fails and rollback fails as well
     *
     */
    @Deprecated
    void delete(LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<?> instanceIdentifier,
        InstanceIdentifier<?> nodeId) throws DTxException.EditFailedException, DTxException.RollbackFailedException;

    /**
     * The method isn't implemented.
     */
    @Deprecated
    @Override <T extends DataObject> void merge(LogicalDatastoreType logicalDatastoreType,
        InstanceIdentifier<T> instanceIdentifier, T t)
        throws
        DTxException.EditFailedException,
        DTxException.RollbackFailedException;

    /**
     * The method isn't implemented.
     */
    @Deprecated
    @Override <T extends DataObject> void merge(LogicalDatastoreType logicalDatastoreType,
        InstanceIdentifier<T> instanceIdentifier, T t, boolean b) throws DTxException.EditFailedException;

    /**
     * The method isn't implemented.
     */
    @Deprecated
    @Override <T extends DataObject> void put(LogicalDatastoreType logicalDatastoreType,
        InstanceIdentifier<T> instanceIdentifier, T t) throws DTxException.EditFailedException;

    /**
     * The method isn't implemented.
     */
    @Deprecated
    @Override <T extends DataObject> void put(LogicalDatastoreType logicalDatastoreType,
        InstanceIdentifier<T> instanceIdentifier, T t, boolean b) throws DTxException.EditFailedException;

    /**
     * Merge data for a specific node. The function is deprecated in distributed-tx because of no return value.
     *
     * @param <T> Class extends DataObject
     * @param logicalDatastoreType datastore type
     * @param instanceIdentifier IID for data
     * @param t data to put to the node
     * @param nodeId IID for node to invoke merge
     *
     * @throws DTxException.EditFailedException thrown when delete fails, but rollback was successful
     * @throws DTxException.RollbackFailedException  thrown when delete fails and rollback fails as well
     */
    @Deprecated
    <T extends DataObject> void merge(LogicalDatastoreType logicalDatastoreType,
        InstanceIdentifier<T> instanceIdentifier, T t, InstanceIdentifier<?> nodeId) throws
        DTxException.EditFailedException;

    /**
     * The method isn't implemented.
     */
    @Deprecated
    <T extends DataObject> void merge(LogicalDatastoreType logicalDatastoreType,
        InstanceIdentifier<T> instanceIdentifier, T t, boolean b, InstanceIdentifier<?> nodeId) throws
        DTxException.EditFailedException;

    /**
     * Put data to a specific node. This function is deprecated in distributed-tx because of no return value.
     *
     * @param <T> Class extends DataObject
     * @param logicalDatastoreType datastore type
     * @param instanceIdentifier IID for data
     * @param nodeId IID for node to invoke put
     * @param t data to put to the node
     *
     * @throws DTxException.EditFailedException thrown when delete fails, but rollback was successful
     * @throws DTxException.RollbackFailedException  thrown when delete fails and rollback fails as well
     */
    @Deprecated
    <T extends DataObject> void put(LogicalDatastoreType logicalDatastoreType,
        InstanceIdentifier<T> instanceIdentifier, T t, InstanceIdentifier<?> nodeId) throws
        DTxException.EditFailedException;

    /**
     * This method isn't implemented.
     */
    @Deprecated
    <T extends DataObject> void put(LogicalDatastoreType logicalDatastoreType,
        InstanceIdentifier<T> instanceIdentifier, T t, boolean b, InstanceIdentifier<?> nodeId) throws
        DTxException.EditFailedException;

    /**
     * Submit the distributed transaction. Rollback of the whole transaction will be performed on failure.
     *
     * @return CheckedFuture indicating the result of submit.
     * <ul>
     * <li> set DTxException.RollbackFailedException if rollback failure</li>
     * <li> set DTxException for other failure</li>
     * <li> set null to the future otherwise</li>
     * </ul>
     *
     * @return CheckedFuture indicating the submit result
     */
    @Override CheckedFuture<Void, TransactionCommitFailedException> submit()
        throws DTxException.SubmitFailedException,
        DTxException.RollbackFailedException;

    /**
     * Cancel the distributed tx
     *
     * Throws UnsupportedOperationException. Not supported in the first release
     *
     */
    @Override boolean cancel()
        throws DTxException.RollbackFailedException;

    /**
     * Marge data to a specific node and rollback of the whole distributed transaction will be performed on failure.
     * The node has to present in the distributed transaction.
     * This API only works for the pure NETCONF distributed transactions.
     *
     * @param <T> Class extends DataObject
     * @param logicalDatastoreType datastore type
     * @param instanceIdentifier IID for data
     * @param t data to put to the node
     * @param nodeId IID for node to invoke delete
     *
     * @return CheckedFuture indicating the result of merge.
     * <ul>
     * <li> set DTxException.ReadFailedException to the future if read failure</li>
     * <li> set DTxException.RollbackFailedException if rollback failure</li>
     * <li> set DTxException for other failure</li>
     * <li> set null to the future otherwise</li>
     * </ul>
     *
     * @throws IllegalArgumentException thrown when the node isn't in the transaction
     */
    <T extends DataObject> CheckedFuture<Void, DTxException> mergeAndRollbackOnFailure(
            final LogicalDatastoreType logicalDatastoreType,
            final InstanceIdentifier<T> instanceIdentifier, final T t, final InstanceIdentifier<?> nodeId);

    /**
     * Marge data to a specific node and rollback of the whole distributed transaction will be performed on failure.
     * The node has to present in the distributed transaction.
     * This API only works for the pure NETCONF distributed transactions.
     *
     * @param <T> Class extends DataObject
     * @param logicalDatastoreType datastore type
     * @param instanceIdentifier IID for data
     * @param t data to put to the node
     * @param nodeId IID for node to invoke delete
     *
     * @return CheckedFuture indicating the result of put
     * <ul>
     * <li> set DTxException.ReadFailedException to the future if read failure</li>
     * <li> set DTxException.RollbackFailedException if rollback failure</li>
     * <li> set DTxException for other failure</li>
     * <li> set null to the future otherwise</li>
     * </ul>
     *
     * @throws IllegalArgumentException thrown when the node isn't in the transaction
     */
    <T extends DataObject> CheckedFuture<Void, DTxException> putAndRollbackOnFailure(
            final LogicalDatastoreType logicalDatastoreType,
            final InstanceIdentifier<T> instanceIdentifier, final T t, final InstanceIdentifier<?> nodeId);

    /**
     * Delete data from a specific node which should be present in the distributed transaction
     * The node has to present in the distributed transaction.
     * This API only works for the pure NETCONF distributed transactions.
     *
     * @param logicalDatastoreType datastore type
     * @param instanceIdentifier IID for data
     * @param nodeId IID for node to invoke delete
     *
     * @return CheckedFuture indicating the result of delete.
     * <ul>
     * <li> set DTxException.ReadFailedException to the future if read failure</li>
     * <li> set DTxException.RollbackFailedException if rollback failure</li>
     * <li> set DTxException for other failure</li>
     * <li> set null to the future otherwise</li>
     * </ul>
     *
     * @throws IllegalArgumentException thrown when the node isn't in the transaction
     */
    CheckedFuture<Void, DTxException> deleteAndRollbackOnFailure(
            final LogicalDatastoreType logicalDatastoreType,
            final InstanceIdentifier<?> instanceIdentifier,
            InstanceIdentifier<?> nodeId);

    /**
     * Rollback the entire transaction.
     *
     * @return CheckedFuture indicating the result of the rollback operation.
     *
     * <ul>
     * <li> set DTxException.RollbackFailedException if rollback failure</li>
     * <li> set null to the future otherwise</li>
     * </ul>
     */
    CheckedFuture<Void, DTxException.RollbackFailedException> rollback();

    /**
     * Marge data to a specific node and rollback of the whole distributed transaction will be performed on failure.
     *
     * @param <T> Class extends DataObject
     * @param logicalTXProviderType transaction provider type
     * @param logicalDatastoreType datastore type
     * @param instanceIdentifier IID for data
     * @param t data to merge to the node
     * @param nodeId IID for node to invoke delete
     *
     * @return CheckedFuture indicating the result of the merge operation.
     * <ul>
     * <li> set DTxException.ReadFailedException to the future if read failure</li>
     * <li> set DTxException.RollbackFailedException if rollback failure</li>
     * <li> set DTxException for other failure</li>
     * <li> set null to the future otherwise</li>
     * </ul>
     *
     * @throws IllegalArgumentException thrown when the node isn't in the transaction
     */
    <T extends DataObject> CheckedFuture<Void, DTxException> mergeAndRollbackOnFailure(
            final DTXLogicalTXProviderType logicalTXProviderType,
            final LogicalDatastoreType logicalDatastoreType,
            final InstanceIdentifier<T> instanceIdentifier, final T t, final InstanceIdentifier<?> nodeId);

    /**
     * Put data to a specific node and rollback of the whole distributed transaction will be performed on failure.
     *
     * @param <T> Class extends DataObject
     * @param logicalTXProviderType transaction provider type
     * @param logicalDatastoreType datastore type
     * @param instanceIdentifier IID for data
     * @param t data to put to the node
     * @param nodeId IID for node to invoke delete
     *
     * @return CheckedFuture indicating the result of the put operation.
     * <ul>
     * <li> set DTxException.ReadFailedException to the future if read failure</li>
     * <li> set DTxException.RollbackFailedException if rollback failure</li>
     * <li> set DTxException for other failure</li>
     * <li> set null to the future otherwise</li>
     * </ul>
     *
     * @throws IllegalArgumentException thrown when the node isn't in the transaction
     */
    <T extends DataObject> CheckedFuture<Void, DTxException> putAndRollbackOnFailure(
            final DTXLogicalTXProviderType logicalTXProviderType,
            final LogicalDatastoreType logicalDatastoreType,
            final InstanceIdentifier<T> instanceIdentifier, final T t, final InstanceIdentifier<?> nodeId);

    /**
     * Delete data from a specific node and rollback of the whole distributed transaction will be performed on failure.
     *
     * @param logicalTXProviderType transaction provider type
     * @param logicalDatastoreType datastore type
     * @param instanceIdentifier IID for data
     * @param nodeId IID for node to invoke delete
     *
     * @return CheckedFuture indicating the result of the merge operation.
     * <ul>
     * <li> set DTxException.ReadFailedException to the future if read failure</li>
     * <li> set DTxException.RollbackFailedException if rollback failure</li>
     * <li> set DTxException for other failure</li>
     * <li> set null to the future otherwise</li>
     * </ul>
     *
     * @throws IllegalArgumentException thrown when the node isn't in the transaction
     */
    CheckedFuture<Void, DTxException> deleteAndRollbackOnFailure(
            final DTXLogicalTXProviderType logicalTXProviderType,
            final LogicalDatastoreType logicalDatastoreType,
            final InstanceIdentifier<?> instanceIdentifier,
            InstanceIdentifier<?> nodeId);
}
