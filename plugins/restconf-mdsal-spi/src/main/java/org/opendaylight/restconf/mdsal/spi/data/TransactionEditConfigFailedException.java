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

    TransactionEditConfigFailedException(final String message, final Throwable cause) {
        super(message, cause);
    }

    TransactionEditConfigFailedException(final String message, final RpcError error) {
        super(message, error);
    }

    TransactionEditConfigFailedException(final String message, final Throwable cause,
            final Collection<RpcError> errors) {
        super(message, cause, errors);
    }

    TransactionEditConfigFailedException(final String message, final Collection<? extends RpcError> errors) {
        super(message, errors);
    }

    TransactionEditConfigFailedException(final String message, final RpcError... errors) {
        super(message, errors);
    }

    TransactionEditConfigFailedException(final String message, final Throwable cause, final RpcError... errors) {
        super(message, cause, errors);
    }
}
