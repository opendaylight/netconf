/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static java.util.Objects.requireNonNull;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.NetconfSessionListener;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.api.messages.NotificationMessage;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.server.api.monitoring.SessionEvent;
import org.opendaylight.netconf.server.api.monitoring.SessionListener;
import org.opendaylight.netconf.server.osgi.NetconfOperationRouterImpl;
import org.opendaylight.netconf.server.spi.SubtreeFilter;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class NetconfServerSessionListener implements NetconfSessionListener<NetconfServerSession> {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfServerSessionListener.class);

    private final SessionListener monitoringSessionListener;
    private final NetconfOperationRouterImpl operationRouter;
    private final AutoCloseable onSessionDownCloseable;

    NetconfServerSessionListener(final NetconfOperationRouterImpl operationRouter,
            final NetconfMonitoringService monitoringService, final AutoCloseable onSessionDownCloseable) {
        this.operationRouter = requireNonNull(operationRouter);
        monitoringSessionListener = monitoringService.getSessionListener();
        this.onSessionDownCloseable = onSessionDownCloseable;
    }

    @Override
    public void onSessionUp(final NetconfServerSession netconfNetconfServerSession) {
        monitoringSessionListener.onSessionUp(netconfNetconfServerSession);
    }

    @Override
    public void onSessionDown(final NetconfServerSession netconfNetconfServerSession, final Exception cause) {
        LOG.debug("Session {} down, reason: {}", netconfNetconfServerSession, cause.getMessage());
        onDown(netconfNetconfServerSession);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onDown(final NetconfServerSession netconfNetconfServerSession) {
        monitoringSessionListener.onSessionDown(netconfNetconfServerSession);

        try {
            operationRouter.close();
        } catch (final Exception closingEx) {
            LOG.debug("Ignoring exception while closing operationRouter", closingEx);
        }
        try {
            onSessionDownCloseable.close();
        } catch (final Exception ex) {
            LOG.debug("Ignoring exception while closing onSessionDownCloseable", ex);
        }
    }

    @Override
    public void onSessionTerminated(final NetconfServerSession netconfNetconfServerSession,
                                    final NetconfTerminationReason netconfTerminationReason) {
        LOG.debug("Session {} terminated, reason: {}", netconfNetconfServerSession,
                netconfTerminationReason.getErrorMessage());
        onDown(netconfNetconfServerSession);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public void onMessage(final NetconfServerSession session, final NetconfMessage netconfMessage) {
        try {

            Preconditions.checkState(operationRouter != null, "Cannot handle message, session up was not yet received");
            // there is no validation since the document may contain yang schemas
            final NetconfMessage message = processDocument(netconfMessage, session);
            LOG.debug("Responding with message {}", message);
            session.sendMessage(message);
            monitoringSessionListener.onSessionEvent(SessionEvent.inRpcSuccess(session));
        } catch (final RuntimeException e) {
            // TODO: should send generic error or close session?
            LOG.error("Unexpected exception", e);
            session.onIncommingRpcFail();
            monitoringSessionListener.onSessionEvent(SessionEvent.inRpcFail(session));
            throw new IllegalStateException("Unable to process incoming message " + netconfMessage, e);
        } catch (final DocumentedException e) {
            LOG.trace("Error occurred while processing message", e);
            session.onOutgoingRpcError();
            session.onIncommingRpcFail();
            monitoringSessionListener.onSessionEvent(SessionEvent.inRpcFail(session));
            monitoringSessionListener.onSessionEvent(SessionEvent.outRpcError(session));
            SendErrorExceptionUtil.sendErrorMessage(session, e, netconfMessage);
        }
    }

    @Override
    public void onError(final NetconfServerSession session, final Exception failure) {
        session.onIncommingRpcFail();
        monitoringSessionListener.onSessionEvent(SessionEvent.inRpcFail(session));
        throw new IllegalStateException("Unable to process incoming message", failure);
    }

    public void onNotification(final NetconfServerSession session, final NotificationMessage notification) {
        monitoringSessionListener.onSessionEvent(SessionEvent.notification(session));
    }

    private NetconfMessage processDocument(final NetconfMessage netconfMessage, final NetconfServerSession session)
            throws DocumentedException {

        final Document incomingDocument = netconfMessage.getDocument();
        final Node rootNode = incomingDocument.getDocumentElement();

        if (rootNode.getLocalName().equals(XmlNetconfConstants.RPC_KEY)) {
            final Document responseDocument = XmlUtil.newDocument();
            checkMessageId(rootNode);

            Document rpcReply = operationRouter.onNetconfMessage(incomingDocument, session);

            rpcReply = SubtreeFilter.applyRpcSubtreeFilter(incomingDocument, rpcReply);

            session.onIncommingRpcSuccess();

            responseDocument.appendChild(responseDocument.importNode(rpcReply.getDocumentElement(), true));
            return new NetconfMessage(responseDocument);
        } else {
            // unknown command, send RFC 4741 p.70 unknown-element
            /*
             * Tag: unknown-element Error-type: rpc, protocol, application
             * Severity: error Error-info: <bad-element> : name of the
             * unexpected element Description: An unexpected element is present.
             */
            throw new DocumentedException("Unknown tag " + rootNode.getNodeName() + " in message:\n" + netconfMessage,
                    ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ELEMENT, ErrorSeverity.ERROR,
                    ImmutableMap.of("bad-element", rootNode.getNodeName()));
        }
    }

    private static void checkMessageId(final Node rootNode) throws DocumentedException {
        final NamedNodeMap attributes = rootNode.getAttributes();
        if (attributes.getNamedItemNS(NamespaceURN.BASE, XmlNetconfConstants.MESSAGE_ID) != null) {
            return;
        }
        if (attributes.getNamedItem(XmlNetconfConstants.MESSAGE_ID) != null) {
            return;
        }

        throw new DocumentedException("Missing attribute " + rootNode.getNodeName(),
                ErrorType.RPC, ErrorTag.MISSING_ATTRIBUTE, ErrorSeverity.ERROR, ImmutableMap.of(
                    "bad-attribute", XmlNetconfConstants.MESSAGE_ID,
                    "bad-element", XmlNetconfConstants.RPC_KEY));
    }
}
