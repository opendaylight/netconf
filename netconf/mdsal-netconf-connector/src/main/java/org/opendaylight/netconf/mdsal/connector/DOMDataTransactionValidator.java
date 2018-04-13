/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector;

import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBrokerExtension;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.yangtools.yang.common.OperationFailedException;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

public interface DOMDataTransactionValidator extends DOMDataBrokerExtension {
    CheckedFuture<Void, DataValidationFailedException> validate(DOMDataReadWriteTransaction transaction);

    class DataValidationFailedException extends OperationFailedException {
        private static final long serialVersionUID = 1L;

        public DataValidationFailedException(final String message, final Throwable cause) {
            super(message, cause, RpcResultBuilder.newError(RpcError.ErrorType.APPLICATION, "invalid-value", message,
                null, null, cause));
        }

        public DataValidationFailedException(final String message) {
            this(message, null);
        }

        public DataValidationFailedException(final Throwable cause) {
            this(cause.getMessage(), cause);
        }
    }
}

