/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.mdsal.spi.data;

import java.util.Collection;
import org.opendaylight.yangtools.yang.common.OperationFailedException;
import org.opendaylight.yangtools.yang.common.RpcError;

/**
 * This exception is raised and returned when transaction edit-config failed.
 */
final class TransactionEditConfigFailedException extends OperationFailedException {

    private final int failedTransactionNumber;

    TransactionEditConfigFailedException(final String message, final Throwable cause,
            final int failedTransactionNumber) {
        super(message, cause);
        this.failedTransactionNumber = failedTransactionNumber;
    }

    TransactionEditConfigFailedException(final String message, final RpcError error,
            final int failedTransactionNumber) {
        super(message, error);
        this.failedTransactionNumber = failedTransactionNumber;
    }

    TransactionEditConfigFailedException(final String message, final Throwable cause,
            final Collection<RpcError> errors, final int failedTransactionNumber) {
        super(message, cause, errors);
        this.failedTransactionNumber = failedTransactionNumber;
    }

    TransactionEditConfigFailedException(final String message, Collection<? extends RpcError> errors,
            final int failedTransactionNumber) {
        super(message, errors);
        this.failedTransactionNumber = failedTransactionNumber;
    }

    TransactionEditConfigFailedException(final String message,
            final int failedTransactionNumber, RpcError... errors) {
        super(message, errors);
        this.failedTransactionNumber = failedTransactionNumber;
    }

    TransactionEditConfigFailedException(final String message, final Throwable cause,
            final int failedTransactionNumber, RpcError... errors) {
        super(message, cause, errors);
        this.failedTransactionNumber = failedTransactionNumber;
    }

    int failedTransactionNumber() {
        return failedTransactionNumber;
    }
}
