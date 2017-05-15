/*
 * Copyright (c) 2017 Frinx s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector.ops;

import static org.opendaylight.netconf.mdsal.connector.ops.Datastore.candidate;
import static org.opendaylight.netconf.mdsal.connector.ops.Datastore.running;

import com.google.common.base.Optional;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants ;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.opendaylight.netconf.mdsal.connector.ops.parser.MdsalNetconfOperation;
import org.opendaylight.netconf.mdsal.connector.ops.parser.MdsalNetconfParameter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class CopyConfig extends MdsalNetconfOperation {
    private static final String OPERATION_NAME = "copy-config";
    private final TransactionProvider transactionProvider;

    public CopyConfig(final String netconfSessionIdForReporting, final TransactionProvider transactionProvider) {
        super(netconfSessionIdForReporting);
        this.transactionProvider = transactionProvider;
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement) throws DocumentedException {
        final MdsalNetconfParameter source =  extractSourceParameter(operationElement);
        final MdsalNetconfParameter target =  extractTargetParameter(operationElement);

        if (candidate == source.getDatastore() && running == target.getDatastore()) {
            return commitTransaction(document);
        } else if (running == source.getDatastore() && candidate == target.getDatastore()) {
            return abortTranction(document);
        }

        return XmlUtil.createElement(document, XmlNetconfConstants.OK, Optional.<String>absent());
    }

    private Element abortTranction(Document document) throws DocumentedException {
        return new DiscardChanges(getNetconfSessionIdForReporting(), transactionProvider).handleWithNoSubsequentOperations(document,null);
    }

    private Element commitTransaction(Document document) throws DocumentedException {
        return new Commit(getNetconfSessionIdForReporting(), transactionProvider).handleWithNoSubsequentOperations(document,null);
    }

    @Override
    protected String getOperationName() {
        return OPERATION_NAME;
    }
}
