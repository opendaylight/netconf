/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal;

import com.google.common.annotations.Beta;
import com.google.common.util.concurrent.FluentFuture;
import java.io.Serial;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataBrokerExtension;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMServiceExtension;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.OperationFailedException;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

/**
 * A {@link DOMServiceExtension} which allows users to provide Validate capability for {@link DOMDataBroker}.
 *
 * <p> See <a href="https://tools.ietf.org/html/rfc4741#section-8.6">RFC4741 section 8.6</a> for details.
 */
@Beta
public interface DOMDataTransactionValidator extends DOMDataBrokerExtension {
    /**
     * Validates state of the data tree associated with the provided {@link DOMDataTreeWriteTransaction}.
     *
     * <p>The operation should not have any side-effects on the transaction state.
     *
     * <p>It can be executed many times, providing the same results if the state of the transaction has not been
     * changed.
     *
     * @param transaction
     *     transaction to be validated
     * @return
     *     a FluentFuture containing the result of the validate operation. The future blocks until the validation
     *     operation is complete. A successful validate returns nothing. On failure, the Future will fail
     *     with a {@link ValidationFailedException} or an exception derived from ValidationFailedException.
     */
    FluentFuture<Void> validate(DOMDataTreeWriteTransaction transaction);

    /**
     * Failed validation of asynchronous transaction. This exception is raised and returned when transaction validation
     * failed.
     */
    class ValidationFailedException extends OperationFailedException {
        @Serial
        private static final long serialVersionUID = 1L;

        public ValidationFailedException(final String message, final Throwable cause) {
            super(message, cause, RpcResultBuilder.newError(ErrorType.APPLICATION, ErrorTag.INVALID_VALUE, message,
                null, null, cause));
        }

        public ValidationFailedException(final String message) {
            this(message, null);
        }
    }
}

