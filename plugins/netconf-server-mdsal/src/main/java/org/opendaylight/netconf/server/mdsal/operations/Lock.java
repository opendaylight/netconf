/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.operations;

import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.server.api.operations.AbstractSingletonNetconfOperation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class Lock extends AbstractSingletonNetconfOperation {
    private static final Logger LOG = LoggerFactory.getLogger(Lock.class);
    private static final String OPERATION_NAME = "lock";
    private static final String TARGET_KEY = "target";

    public Lock(final SessionIdType sessionId) {
        super(sessionId);
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement)
            throws DocumentedException {
        final Datastore targetDatastore = extractTargetParameter(operationElement);
        if (targetDatastore == Datastore.candidate) {
            LOG.debug("Locking candidate datastore on session: {}", sessionId().getValue());
            return document.createElement(XmlNetconfConstants.OK);
        }

        throw new DocumentedException("Unable to lock " + targetDatastore + " datastore",
                ErrorType.APPLICATION, ErrorTag.OPERATION_NOT_SUPPORTED, ErrorSeverity.ERROR);
    }

    static Datastore extractTargetParameter(final XmlElement operationElement) throws DocumentedException {
        final XmlElement targetElement = operationElement.getOnlyChildElementWithSameNamespace(TARGET_KEY);
        final XmlElement targetChildNode = targetElement.getOnlyChildElementWithSameNamespace();
        return Datastore.valueOf(targetChildNode.getName());
    }

    @Override
    protected String getOperationName() {
        return OPERATION_NAME;
    }
}
