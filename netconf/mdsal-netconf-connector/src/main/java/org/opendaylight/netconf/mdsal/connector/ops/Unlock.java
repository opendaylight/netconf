/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops;

import com.google.common.base.Optional;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.mdsal.connector.ops.parser.MdsalNetconfOperation;
import org.opendaylight.netconf.mdsal.connector.ops.parser.MdsalNetconfParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class Unlock extends MdsalNetconfOperation {

    private static final Logger LOG = LoggerFactory.getLogger(Unlock.class);

    private static final String OPERATION_NAME = "unlock";

    public Unlock(final String netconfSessionIdForReporting) {
        super(netconfSessionIdForReporting, null);
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement) throws DocumentedException {
        final MdsalNetconfParameter inputParameter = extractTargetParameter(operationElement);
        if (inputParameter.getDatastore() == Datastore.candidate) {
            LOG.debug("Unlocking candidate datastore on session: {}", getNetconfSessionIdForReporting());
            return XmlUtil.createElement(document, XmlNetconfConstants.OK, Optional.<String>absent());
        }

        throw new DocumentedException("Unable to unlock " + inputParameter.getDatastore() + " datastore", DocumentedException.ErrorType.application,
                DocumentedException.ErrorTag.operation_not_supported, DocumentedException.ErrorSeverity.error);
    }

    @Override
    protected String getOperationName() {
        return OPERATION_NAME;
    }

}
