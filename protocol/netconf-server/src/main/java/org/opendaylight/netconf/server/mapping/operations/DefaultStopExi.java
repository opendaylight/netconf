/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mapping.operations;

import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.server.NetconfServerSession;
import org.opendaylight.netconf.server.api.operations.AbstractSingletonNetconfOperation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class DefaultStopExi extends AbstractSingletonNetconfOperation implements DefaultNetconfOperation {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultStopExi.class);

    public static final String STOP_EXI = "stop-exi";
    private NetconfServerSession netconfSession;

    public DefaultStopExi(final SessionIdType sessionId) {
        super(sessionId);
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement) {
        LOG.debug("Received stop-exi message {} ", XmlUtil.toString(operationElement));

        netconfSession.stopExiCommunication();

        Element getSchemaResult = document.createElementNS(NamespaceURN.BASE, XmlNetconfConstants.OK);
        LOG.trace("{} operation successful", STOP_EXI);
        return getSchemaResult;
    }

    @Override
    protected String getOperationName() {
        return STOP_EXI;
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
