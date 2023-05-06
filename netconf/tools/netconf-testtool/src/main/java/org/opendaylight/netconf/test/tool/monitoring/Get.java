/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.monitoring;

import java.util.Map;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.server.api.operations.AbstractNetconfOperation;
import org.opendaylight.netconf.server.api.operations.HandlingPriority;
import org.opendaylight.netconf.server.api.operations.NetconfOperationChainedExecution;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class Get extends AbstractNetconfOperation {
    private static final Logger LOG = LoggerFactory.getLogger(Get.class);

    private final NetconfMonitoringService netconfMonitor;

    public Get(final SessionIdType sessionId, final NetconfMonitoringService netconfMonitor) {
        super(sessionId);
        this.netconfMonitor = netconfMonitor;
    }

    @Override
    protected String getOperationName() {
        return XmlNetconfConstants.GET;
    }

    @Override
    protected HandlingPriority getHandlingPriority() {
        return HandlingPriority.HANDLE_WITH_DEFAULT_PRIORITY.increasePriority(1);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public Document handle(final Document requestMessage, final NetconfOperationChainedExecution subsequentOperation)
            throws DocumentedException {
        if (subsequentOperation.isExecutionTermination()) {
            throw new DocumentedException(String.format("Subsequent netconf operation expected by %s", this),
                    ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, ErrorSeverity.ERROR);
        }

        try {
            final Document innerResult = subsequentOperation.execute(requestMessage);

            final NetconfState netconfMonitoring = new NetconfState(netconfMonitor);
            Element monitoringXmlElement = new JaxBSerializer().toXml(netconfMonitoring);

            monitoringXmlElement = (Element) innerResult.importNode(monitoringXmlElement, true);
            final Element monitoringXmlElementPlaceholder = getPlaceholder(innerResult);
            monitoringXmlElementPlaceholder.appendChild(monitoringXmlElement);

            return innerResult;
        } catch (final RuntimeException e) {
            final String errorMessage = "Get operation for netconf-state subtree failed";
            LOG.warn(errorMessage, e);

            throw new DocumentedException(errorMessage, e,
                    ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, ErrorSeverity.ERROR,
                    // FIXME: i.e. <error>e.getMessage()</error> ?
                    Map.of(ErrorSeverity.ERROR.elementBody(), e.getMessage()));
        }
    }

    @Override
    protected Element handle(final Document document, final XmlElement message,
                             final NetconfOperationChainedExecution subsequentOperation) {
        throw new UnsupportedOperationException("Never gets called");
    }

    private static Element getPlaceholder(final Document innerResult) throws DocumentedException {
        return XmlElement.fromDomElementWithExpected(innerResult.getDocumentElement(),
            XmlNetconfConstants.RPC_REPLY_KEY, NamespaceURN.BASE)
            .getOnlyChildElement(XmlNetconfConstants.DATA_KEY)
            .getDomElement();
    }
}
