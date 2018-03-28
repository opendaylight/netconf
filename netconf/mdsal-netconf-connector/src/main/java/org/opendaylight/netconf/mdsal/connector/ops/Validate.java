/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.DocumentedException.ErrorSeverity;
import org.opendaylight.netconf.api.DocumentedException.ErrorTag;
import org.opendaylight.netconf.api.DocumentedException.ErrorType;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class Validate extends AbstractConfigOperation {
    private static final Logger LOG = LoggerFactory.getLogger(Validate.class);
    private static final String OPERATION_NAME = "validate";
    private static final String SOURCE_KEY = "source";

    private final TransactionProvider transactionProvider;

    public Validate(final String netconfSessionIdForReporting, final TransactionProvider transactionProvider) {
        super(netconfSessionIdForReporting);
        this.transactionProvider = transactionProvider;
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement)
        throws DocumentedException {
        final Datastore targetDatastore = extractSourceParameter(operationElement, OPERATION_NAME);
        if (targetDatastore != Datastore.candidate) {
            throw new DocumentedException("<validate> is only supported on candidate datastore",
                DocumentedException.ErrorType.PROTOCOL,
                ErrorTag.OPERATION_NOT_SUPPORTED,
                ErrorSeverity.ERROR);
        }

        boolean validateStatus = transactionProvider.validateTransaction();
        LOG.trace("<validate> completed successfully {}", validateStatus);
        return XmlUtil.createElement(document, XmlNetconfConstants.OK, Optional.absent());
    }

    protected static Datastore extractSourceParameter(final XmlElement operationElement, final String operationName)
        throws DocumentedException {
        final NodeList elementsByTagName = getElementsByTagName(operationElement, SOURCE_KEY);
        // Direct lookup instead of using XmlElement class due to performance
        if (elementsByTagName.getLength() == 0) {
            final Map<String, String> errorInfo = ImmutableMap.of("bad-attribute", SOURCE_KEY, "bad-element",
                operationName);
            throw new DocumentedException("Missing source element", ErrorType.PROTOCOL, ErrorTag.MISSING_ELEMENT,
                ErrorSeverity.ERROR, errorInfo);
        } else if (elementsByTagName.getLength() > 1) {
            throw new DocumentedException("Multiple source elements", ErrorType.RPC, ErrorTag.UNKNOWN_ATTRIBUTE,
                ErrorSeverity.ERROR);
        } else {
            final XmlElement sourceChildNode =
                XmlElement.fromDomElement((Element) elementsByTagName.item(0)).getOnlyChildElement();
            return Datastore.valueOf(sourceChildNode.getName());
        }
    }

    @Override
    protected String getOperationName() {
        return OPERATION_NAME;
    }
}
