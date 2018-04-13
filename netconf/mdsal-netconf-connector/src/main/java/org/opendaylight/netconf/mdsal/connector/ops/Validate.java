/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorSeverity;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorTag;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorType;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.opendaylight.netconf.util.mapping.AbstractSingletonNetconfOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class Validate extends AbstractSingletonNetconfOperation {
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
        final Datastore sourceDatastore = extractSourceParameter(operationElement);
        if (sourceDatastore == Datastore.running) {
            throw new DocumentedException("validate of running datastore is not supported",
                ErrorType.PROTOCOL,
                ErrorTag.OPERATION_NOT_SUPPORTED,
                ErrorSeverity.ERROR);
        }

        boolean validateStatus = transactionProvider.validateTransaction();
        LOG.trace("Validate completed successfully {}", validateStatus);
        return XmlUtil.createElement(document, XmlNetconfConstants.OK, Optional.absent());
    }

    private static Datastore extractSourceParameter(final XmlElement operationElement) throws DocumentedException {
        final NodeList elementsByTagName = getElementsByTagName(operationElement, SOURCE_KEY);
        // Direct lookup instead of using XmlElement class due to performance
        if (elementsByTagName.getLength() == 0) {
            final Map<String, String> errorInfo = ImmutableMap.of("bad-attribute", SOURCE_KEY, "bad-element",
                OPERATION_NAME);
            throw new DocumentedException("Missing source element", ErrorType.PROTOCOL, ErrorTag.MISSING_ATTRIBUTE,
                ErrorSeverity.ERROR, errorInfo);
        } else if (elementsByTagName.getLength() > 1) {
            throw new DocumentedException("Multiple source elements", ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ELEMENT,
                ErrorSeverity.ERROR);
        } else {
            final XmlElement sourceChildNode =
                XmlElement.fromDomElement((Element) elementsByTagName.item(0)).getOnlyChildElement();
            return Datastore.valueOf(sourceChildNode.getName());
        }
    }

    @VisibleForTesting
    static NodeList getElementsByTagName(final XmlElement operationElement, final String key) throws
        DocumentedException {
        final Element element = operationElement.getDomElement();
        final NodeList elementsByTagName;

        if (Strings.isNullOrEmpty(element.getPrefix())) {
            elementsByTagName = element.getElementsByTagName(key);
        } else {
            elementsByTagName = element.getElementsByTagNameNS(operationElement.getNamespace(), key);
        }

        return elementsByTagName;
    }

    @Override
    protected String getOperationName() {
        return OPERATION_NAME;
    }

}
