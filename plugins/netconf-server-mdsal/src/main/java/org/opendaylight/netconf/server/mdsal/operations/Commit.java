/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.operations;

import static java.util.Objects.requireNonNull;

import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.server.api.operations.AbstractSingletonNetconfOperation;
import org.opendaylight.netconf.server.mdsal.TransactionProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class Commit extends AbstractSingletonNetconfOperation {
    private static final Logger LOG = LoggerFactory.getLogger(Commit.class);
    private static final String OPERATION_NAME = "commit";

    private final TransactionProvider transactionProvider;

    public Commit(final SessionIdType sessionId, final TransactionProvider transactionProvider) {
        super(sessionId);
        this.transactionProvider = requireNonNull(transactionProvider);
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
