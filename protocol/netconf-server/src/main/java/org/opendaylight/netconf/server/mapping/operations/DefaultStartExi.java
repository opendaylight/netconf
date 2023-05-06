/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mapping.operations;

import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.server.NetconfServerSession;
import org.opendaylight.netconf.server.api.operations.AbstractSingletonNetconfOperation;
import org.opendaylight.netconf.server.api.operations.NetconfOperationChainedExecution;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class DefaultStartExi extends AbstractSingletonNetconfOperation implements DefaultNetconfOperation {
    public static final String START_EXI = "start-exi";

    private static final Logger LOG = LoggerFactory.getLogger(DefaultStartExi.class);
    private NetconfServerSession netconfSession;

    public DefaultStartExi(final SessionIdType sessionId) {
        super(sessionId);
    }

    @Override
    public Document handle(final Document message,
                           final NetconfOperationChainedExecution subsequentOperation) throws DocumentedException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Received start-exi message {} ", XmlUtil.toString(message));
        }

        try {
            netconfSession.startExiCommunication(new NetconfMessage(message));
        } catch (final IllegalArgumentException e) {
            throw new DocumentedException("Failed to parse EXI parameters", e, ErrorType.PROTOCOL,
                    ErrorTag.OPERATION_FAILED, ErrorSeverity.ERROR);
        }

        return super.handle(message, subsequentOperation);
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document,
                                                       final XmlElement operationElement) {
        final Element getSchemaResult = document.createElementNS(NamespaceURN.BASE, XmlNetconfConstants.OK);
        LOG.trace("{} operation successful", START_EXI);
        return getSchemaResult;
    }

    @Override
    protected String getOperationName() {
        return START_EXI;
    }

    @Override
    protected String getOperationNamespace() {
        return NamespaceURN.EXI;
    }

    @Override
    public void setNetconfSession(final NetconfServerSession netconfServerSession) {
        netconfSession = netconfServerSession;
    }
}
