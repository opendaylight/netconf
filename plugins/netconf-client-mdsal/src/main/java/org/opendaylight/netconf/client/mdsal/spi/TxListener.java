/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

interface TxListener {
    /**
     * Invoked, when transaction completes successfully.
     * @param transaction transaction
     */
    void onTransactionSuccessful(AbstractWriteTx transaction);

    /**
     * Invoked, when transaction fails.
     *
     * @param transaction transaction
     * @param cause cause
     */
    void onTransactionFailed(AbstractWriteTx transaction, Throwable cause);

    /**
     * Invoked, when transaction is cancelled.
     * @param transaction transaction
     */
    void onTransactionCancelled(AbstractWriteTx transaction);

    /**
     * Invoked, when transaction is submitted.
     * @param transaction transaction
     */
    void onTransactionSubmitted(AbstractWriteTx transaction);
}
