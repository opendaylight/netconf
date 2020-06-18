/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8639.layer.services.impl;

import com.google.common.base.Preconditions;
import java.util.Optional;
import java.util.regex.Matcher;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.glassfish.jersey.media.sse.EventOutput;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfInvokeOperationsService;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.TransactionVarsWrapper;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.ReadDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.restconf.nb.rfc8639.layer.services.api.SubscribedNotifications;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.NotificationsHolder;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.RegisteredNotificationWrapper;
import org.opendaylight.restconf.nb.rfc8639.layer.web.jetty.server.ServletInfo;
import org.opendaylight.restconf.nb.rfc8639.util.services.SubscribedNotificationsUtil;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/")
public class SubscribedNotificationsImpl implements SubscribedNotifications {

    private static final Logger LOG = LoggerFactory.getLogger(SubscribedNotificationsImpl.class);
    private static final String SSL_SESSION_ID = "javax.servlet.request.ssl_session_id";

    private final RestconfInvokeOperationsService restconfInvokeOperationsService;
    private final TransactionChainHandler transactionChainHandler;
    private final SchemaContextHandler schemaContextHandler;
    private final DOMMountPointServiceHandler mountPointServiceHandler;
    private final NotificationsHolder holder;
    private final ServletInfo servletInfo;

    public SubscribedNotificationsImpl(final RestconfInvokeOperationsService restconfInvokeOperationsService,
            final TransactionChainHandler transactionChainHandler, final SchemaContextHandler schemaContextHandler,
            final DOMMountPointServiceHandler mountPointServiceHandler, final NotificationsHolder holder,
            final ServletInfo servletInfo) {
        this.restconfInvokeOperationsService = restconfInvokeOperationsService;
        this.transactionChainHandler = transactionChainHandler;
        this.schemaContextHandler = schemaContextHandler;
        this.mountPointServiceHandler = mountPointServiceHandler;
        this.holder = holder;
        this.servletInfo = servletInfo;
    }

    @Override
    public Response getStreams(final UriInfo uriInfo) {
        final InstanceIdentifierContext instanceIdentifier = ParserIdentifier.toInstanceIdentifier(
                Rfc8040.MonitoringModule.PATH_TO_STREAMS, this.schemaContextHandler.get(), Optional.of(
                        this.mountPointServiceHandler.get()));
        final DOMMountPoint mountPoint = instanceIdentifier.getMountPoint();
        final TransactionChainHandler actualTransactionChainHandler = mountPoint == null ? this.transactionChainHandler
                : SubscribedNotificationsUtil.resolveMountPointTransaction(mountPoint);

        final TransactionVarsWrapper transactionNode = new TransactionVarsWrapper(instanceIdentifier, mountPoint,
                actualTransactionChainHandler);

        // read all streams from DS
        final NormalizedNode<?, ?> node = ReadDataTransactionUtil.readData(
                ReadDataTransactionUtil.parseUriParameters(instanceIdentifier, uriInfo).getContent(),
                transactionNode, null);

        // create response with resolved streams
        return Response.status(200).entity(new NormalizedNodeContext(instanceIdentifier, node)).build();
    }

    @Override
    public EventOutput listen(final String streamName, final String subscriptionId,
            final HttpServletRequest httpServletRequest) {
        final Uint32 subscriptionIdNum;
        try {
            subscriptionIdNum = Uint32.valueOf(subscriptionId);
        } catch (final NumberFormatException e) {
            throw new RestconfDocumentedException("Invalid subscription-id in the request URI: " + subscriptionId
                    + ". It must be a uint32 number.", RestconfError.ErrorType.PROTOCOL,
                    RestconfError.ErrorTag.INVALID_VALUE, e);
        }

        final RegisteredNotificationWrapper notificationSubscription = this.holder.getNotification(subscriptionIdNum);
        if (notificationSubscription == null) {
            throw new RestconfDocumentedException("Notification stream subscription with id " + subscriptionId
                    + " does not exist.", RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.INVALID_VALUE);
        }

        final NotificationDefinition streamSubscriptionNotificationDef = notificationSubscription
                .getSubscriptionNotificationListener().getNotificationDefinition();
        final String modulePrefixAndName = SubscribedNotificationsUtil.qNameToModulePrefixAndName(
                streamSubscriptionNotificationDef.getQName(), this.schemaContextHandler.get());

        final Matcher matcher = SubscribedNotificationsUtil.PREFIXED_NOTIFICATION_STREAM_NAME_PATTERN
                .matcher(streamName);
        if (!matcher.matches()) {
            throw new RestconfDocumentedException("Name of the notification stream in the request URI should be "
                    + "prefixed with the name of the module to which it belongs."
                    + "The correct form is module:notification."
                    + "In case of this subscription it should be: "
                    + modulePrefixAndName, RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.INVALID_VALUE);
        }

        if (!modulePrefixAndName.equals(streamName)) {
            throw new RestconfDocumentedException("Notification stream subscription with id " + subscriptionId
                    + " does not belong to stream: '" + streamName + "'.", RestconfError.ErrorType.PROTOCOL,
                    RestconfError.ErrorTag.INVALID_VALUE);
        }

        final EventOutput eventOutput = new EventOutput();
        notificationSubscription.getSubscriptionNotificationListener().addEventOutput(eventOutput);
        notificationSubscription.getSubscriptionNotificationListener().replayNotifications();
        return eventOutput;
    }

    @Override
    public NormalizedNodeContext invokeRpc(final String identifier, final NormalizedNodeContext payload,
            final UriInfo uriInfo, final HttpServletRequest httpServletRequest) {
        Preconditions.checkNotNull(identifier);
        if (identifier.equals("ietf-subscribed-notifications:establish-subscription")
                || identifier.equals("ietf-subscribed-notifications:modify-subscription")
                || identifier.equals("ietf-subscribed-notifications:delete-subscription")) {
            this.servletInfo.setSessionId((String) httpServletRequest.getAttribute(SSL_SESSION_ID));
        } else {
            LOG.debug("Invoke non-notifiable operation.");
        }
        return this.restconfInvokeOperationsService.invokeRpc(identifier, payload, uriInfo);
    }
}
