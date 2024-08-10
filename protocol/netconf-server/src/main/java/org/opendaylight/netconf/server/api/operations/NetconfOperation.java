/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.server.api.operations;

import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.api.DocumentedException;
import org.w3c.dom.Document;

/**
 * NetconfOperation handles netconf requests. Multiple operations might be
 * capable of handling one request at the same time. In such case, these
 * operations are chained (ordered by HandlingPriority returned by canHandle
 * method) and executed.
 *
 * <p>
 * Operation can be declared as singleton or last in chain (see abstract
 * implementations in netconf-util). If the operation is not singleton or last,
 * it is responsible for the execution of subsequent operation and for merging
 * the results.
 */
public interface NetconfOperation {
    /**
     * Singleton operations should return {@link HandlingPriority#HANDLE_WITH_MAX_PRIORITY}, last operations
     * {@link HandlingPriority#HANDLE_WITH_DEFAULT_PRIORITY}.
     *
     * @param message request message
     * @return A {@link HandlingPriority}, or {@code null} if the message is not handled
     * @throws DocumentedException if the message is found to have structural errors
     */
    @Nullable HandlingPriority canHandle(Document message) throws DocumentedException;

    /**
     * Execute current netconf operation and trigger execution of subsequent operations, if applicable.
     *
     * @param requestMessage request message
     * @param subsequentOperation execution of subsequent NETCONF operation, or {@code null}
     * @return {@code document}
     * @throws DocumentedException if operation fails
     */
    Document handle(Document requestMessage, @Nullable NetconfOperationChainedExecution subsequentOperation)
            throws DocumentedException;
}
