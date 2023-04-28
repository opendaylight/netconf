/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.api.operations;

import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public abstract class AbstractSingletonNetconfOperation extends AbstractLastNetconfOperation {
    protected AbstractSingletonNetconfOperation(final SessionIdType sessionId) {
        super(sessionId);
    }

    @Override
    protected Element handle(final Document document, final XmlElement operationElement,
                             final NetconfOperationChainedExecution subsequentOperation) throws DocumentedException {
        return handleWithNoSubsequentOperations(document, operationElement);
    }

    @Override
    protected HandlingPriority getHandlingPriority() {
        return HandlingPriority.HANDLE_WITH_MAX_PRIORITY;
    }
}
