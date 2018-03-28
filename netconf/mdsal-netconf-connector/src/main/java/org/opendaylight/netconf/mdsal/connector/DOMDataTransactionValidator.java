/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector;

import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBrokerExtension;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMServiceExtension;
import org.opendaylight.yangtools.yang.common.OperationFailedException;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

/**
 * A {@link DOMServiceExtension} which allows users to provide Validate capability for {@link DOMDataBroker}.
 *
 * <p> See <a href="https://tools.ietf.org/html/rfc4741#section-8.6">RFC4741 section 8.6</a> for details.
 */
public interface DOMDataTransactionValidator extends DOMDataBrokerExtension {
    /**
     * Validates state of the data tree associated with the provided {@link DOMDataWriteTransaction}.
     *
     * <p>The operation should not have any side-effects on the transaction state.
     *
     * <p>It can be executed many times, providing the same results if the state of the transaction has not been
     * changed.
     *
     * @param transaction
     *     transaction to be validated
     * @return
     *     a CheckFuture containing the result of the validate operation. The Future blocks until the validation
     *     operation is complete. A successful validate returns nothing. On failure, the Future will fail
     *     with a  {@link ValidationFailedException} or an exception derived from ValidationFailedException.
     */
    CheckedFuture<Void, ValidationFailedException> validate(DOMDataWriteTransaction transaction);

    /**
     * Failed validation of asynchronous transaction. This exception is raised and returned when transaction validation
     * failed.
     */
    class ValidationFailedException extends OperationFailedException {
        private static final long serialVersionUID = 1L;

        public ValidationFailedException(final String message, final Throwable cause) {
            super(message, cause, RpcResultBuilder.newError(RpcError.ErrorType.APPLICATION, "invalid-value", message,
                null, null, cause));
        }

        public ValidationFailedException(final String message) {
            this(message, null);
        }
    }
}

