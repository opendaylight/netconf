/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.mdsal.spi.exception;

import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.yangtools.yang.common.RpcError;

/**
 * This exception is raised and returned when transaction edit-config failed.
 */
public class TransactionLockFailedException extends TransactionCommitFailedException {
    public TransactionLockFailedException(String message, RpcError... errors) {
        super(message, errors);
    }

    public TransactionLockFailedException(String message, Throwable cause, RpcError... errors) {
        super(message, cause, errors);
    }
}
