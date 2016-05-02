/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.api;

import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Provider of distributed transactions.
 * Applications request the provider for a distributed transaction.
 *
 */
public interface DTxProvider {

    /**
     * Instantiate a new distributed transaction with only NETCONF provider.
     *
     * @param nodes set of instance IDs for nodes participating in a distributed tx.
     *
     * @return new distributed Tx for a set of nodes.
     * Per node transaction was successfully initialized for each node at this point.
     *
     * @throws DTxException.DTxInitializationFailedException if:
     * <ul>
     * <li> Unknown node was specified</li>
     * <li> Node is used by other distributed transaction</li>
     * <li> Node tx could not be initialized (node is in use by other client/is unreachable etc)</li>
     * </ul>
     */
    @Nonnull DTx newTx(@Nonnull Set<InstanceIdentifier<?>> nodes) throws DTxException.DTxInitializationFailedException;

    /**
     * Instantiate a new distributed transaction containing different providers.
     *
     * @param nodes maps of instance IDs for nodes participating from one or multiple providers in a distributed tx. Supported providers are
     * <ul>
     * <li> NETCONF_TX_PROVIDER </li>
     * <li>DATASTORE_TX_PROVIDER</li>
     * </ul>
     * @param nodes Maps of sets of instance IDs for nodes participating in a distributed tx corresponding to each tx providers.
     *
     * @return new distributed Tx. Per node transaction was successfully initialized for each node at this point.
     *
     * @throws DTxException.DTxInitializationFailedException if:
     * <ul>
     * <li> Unknown node was specified</li>
     * <li> Node is used by other distributed transaction</li>
     * <li> Node tx could not be initialized (node is in use by other client/is unreachable etc)</li>
     * </ul>
     */
    @Nonnull DTx newTx(@Nonnull Map<DTXLogicalTXProviderType, Set<InstanceIdentifier<?>>> nodes) throws DTxException.DTxInitializationFailedException;
}
