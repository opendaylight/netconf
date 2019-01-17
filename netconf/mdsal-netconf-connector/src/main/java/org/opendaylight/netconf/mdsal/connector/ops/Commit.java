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
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.opendaylight.netconf.util.mapping.AbstractSingletonNetconfOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

// FIXME duplicated code
// netconf/netconf/config-netconf-connector/src/main/java/org/opendaylight/netconf/confignetconfconnector/Commit.java
public class Commit extends AbstractSingletonNetconfOperation {

    private static final Logger LOG = LoggerFactory.getLogger(Commit.class);

    private static final String OPERATION_NAME = "commit";
    private final TransactionProvider transactionProvider;

    public Commit(final String netconfSessionIdForReporting, final TransactionProvider transactionProvider) {
        super(netconfSessionIdForReporting);
        this.transactionProvider = transactionProvider;

    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement)
            throws DocumentedException {

        boolean commitStatus = transactionProvider.commitTransaction();
        LOG.trace("Commit completed successfully {}", commitStatus);

        return document.createElement(XmlNetconfConstants.OK);
    }

    @Override
    protected String getOperationName() {
        return OPERATION_NAME;
    }
}
