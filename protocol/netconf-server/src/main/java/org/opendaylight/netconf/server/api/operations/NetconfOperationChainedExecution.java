/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.api.operations;

import org.opendaylight.netconf.api.DocumentedException;
import org.w3c.dom.Document;

/**
 * Single link in netconf operation execution chain.
 * Wraps the execution of a single netconf operation.
 */
public interface NetconfOperationChainedExecution {
    /**
     * Check if this is termination point in operation execution.
     *
     * @return true if this is termination point in operation execution, false
     *     if there is a subsequent operation present that needs to be
     *     executed.
     */
    boolean isExecutionTermination();

    /**
     * Do not execute if this is termination point.
     */
    Document execute(Document requestMessage) throws DocumentedException;

    NetconfOperationChainedExecution EXECUTION_TERMINATION_POINT = new NetconfOperationChainedExecution() {
        @Override
        public boolean isExecutionTermination() {
            return true;
        }

        @Override
        public Document execute(final Document requestMessage) {
            throw new IllegalStateException("This execution represents the termination point in operation execution "
                    + "and cannot be executed itself");
        }
    };
}
