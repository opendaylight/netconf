/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.impl.mapping.operations;

import com.google.common.base.Optional;
import java.util.Collections;
import java.util.Map;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.impl.NetconfServerSession;
import org.opendaylight.netconf.impl.osgi.NetconfOperationRouter;
import org.opendaylight.netconf.impl.osgi.NetconfSessionDatastore;
import org.opendaylight.netconf.util.mapping.AbstractSingletonNetconfOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class DefaultKillSession extends AbstractSingletonNetconfOperation  {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultKillSession.class);

    public static final String KILL_SESSION = "kill-session";
    public static final String SESSION_ID_KEY = "session-id";

    private final NetconfSessionDatastore netconfSessionDatastore;

    public DefaultKillSession(String netconfSessionIdForReporting, NetconfSessionDatastore netconfSessionDatastore) {
        super(netconfSessionIdForReporting);
        this.netconfSessionDatastore = netconfSessionDatastore;
    }

    @Override
    protected String getOperationName() {
        return KILL_SESSION;
    }

    private Long extractSessionId(XmlElement operationElement) throws DocumentedException {
        NodeList elementsByTagName = operationElement.getDomElement().getElementsByTagName(SESSION_ID_KEY);
        if (elementsByTagName.getLength() == 1) {
            try {
                return Long.parseLong(elementsByTagName.item(0).getTextContent());
            } catch (NumberFormatException e) {
                LOG.error(e.getMessage(),e);
                throw new DocumentedException("Invalid value in <session-id>: "+elementsByTagName.item(0).getTextContent(),
                        DocumentedException.ErrorType.application,
                        DocumentedException.ErrorTag.operation_failed,
                        DocumentedException.ErrorSeverity.error);
            }

        }
        throw new DocumentedException("Invalid value in <session-id>",
                DocumentedException.ErrorType.application,
                DocumentedException.ErrorTag.operation_failed,
                DocumentedException.ErrorSeverity.error);
    }

    @Override
    protected Element handleWithNoSubsequentOperations(Document document, XmlElement operationElement) throws DocumentedException {
        Long sessionId = extractSessionId(operationElement);

        Optional<Map.Entry<NetconfServerSession, NetconfOperationRouter>> sessionPairOpt = netconfSessionDatastore.getSessionDatastore(sessionId);
        if (sessionPairOpt.isPresent()) {
            if (Long.valueOf(getNetconfSessionIdForReporting()).equals(sessionPairOpt.get())) {
                throw new DocumentedException("Kill own session is not supported",
                        DocumentedException.ErrorType.application,
                        DocumentedException.ErrorTag.operation_failed,
                        DocumentedException.ErrorSeverity.error);
            }
            return killSession(document, sessionPairOpt.get());
        }

        throw new DocumentedException("The session id: " + sessionId + " doesn't exist. Executed by:"
                + getNetconfSessionIdForReporting(), DocumentedException.ErrorType.application,
                DocumentedException.ErrorTag.operation_failed,
                DocumentedException.ErrorSeverity.error);
    }

    private Element killSession(Document document, Map.Entry<NetconfServerSession, NetconfOperationRouter> sessionPair) throws DocumentedException {
        try {
            sessionPair.getKey().close();
            sessionPair.getValue().close();
        } catch (Exception e) {
            throw new DocumentedException("Unable to properly close session "
                    + getNetconfSessionIdForReporting(), DocumentedException.ErrorType.application,
                    DocumentedException.ErrorTag.operation_failed,
                    DocumentedException.ErrorSeverity.error, Collections.singletonMap(
                    DocumentedException.ErrorSeverity.error.toString(), e.getMessage()));
        }
        return XmlUtil.createElement(document, XmlNetconfConstants.OK, Optional.<String>absent());
    }

}
