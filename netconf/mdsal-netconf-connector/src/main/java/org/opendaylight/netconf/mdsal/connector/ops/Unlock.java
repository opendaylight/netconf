/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector.ops;

import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.util.mapping.AbstractSingletonNetconfOperation;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/* FIXME Duplicated code
   netconf/netconf/config-netconf-connector/src/main/java/org/opendaylight/netconf/confignetconfconnector/
   operations/UnLock.java
*/
public class Unlock extends AbstractSingletonNetconfOperation {
    private static final Logger LOG = LoggerFactory.getLogger(Unlock.class);
    private static final String OPERATION_NAME = "unlock";

    public Unlock(final String netconfSessionIdForReporting) {
        super(netconfSessionIdForReporting);
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement)
            throws DocumentedException {
        final Datastore targetDatastore = Lock.extractTargetParameter(operationElement);
        if (targetDatastore == Datastore.candidate) {
            LOG.debug("Unlocking candidate datastore on session: {}", getNetconfSessionIdForReporting());
            return document.createElement(XmlNetconfConstants.OK);
        }

        throw new DocumentedException("Unable to unlock " + targetDatastore + " datastore",
                ErrorType.APPLICATION, ErrorTag.OPERATION_NOT_SUPPORTED, ErrorSeverity.ERROR);
    }

    @Override
    protected String getOperationName() {
        return OPERATION_NAME;
    }
}
