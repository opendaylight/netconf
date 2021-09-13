/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import java.util.Optional;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenersBroker;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.NotificationListenerAdapter;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

/**
 * Utility class for creation of data-change-event or YANG notification streams.
 */
final class CreateStreamUtil {
    private CreateStreamUtil() {
        // Hidden on purpose
    }

    /**
     * Create YANG notification stream using notification definition in YANG schema.
     *
     * @param notificationDefinition YANG notification definition.
     * @param refSchemaCtx           Reference to {@link EffectiveModelContext}
     * @param outputType             Output type (XML or JSON).
     * @return {@link NotificationListenerAdapter}
     */
    static NotificationListenerAdapter createYangNotifiStream(final NotificationDefinition notificationDefinition,
            final EffectiveModelContext refSchemaCtx, final NotificationOutputType outputType) {
        final String streamName = parseNotificationStreamName(requireNonNull(notificationDefinition),
                requireNonNull(refSchemaCtx), requireNonNull(outputType.getName()));
        final Optional<NotificationListenerAdapter> listenerForStreamName = ListenersBroker.getInstance()
                .getNotificationListenerFor(streamName);
        return listenerForStreamName.orElseGet(() -> ListenersBroker.getInstance().registerNotificationListener(
                Absolute.of(ImmutableList.copyOf(notificationDefinition.getPath().getPathFromRoot())), streamName,
                outputType));
    }

    private static String parseNotificationStreamName(final NotificationDefinition notificationDefinition,
            final EffectiveModelContext refSchemaCtx, final String outputType) {
        final QName notificationDefinitionQName = notificationDefinition.getQName();
        final Module module = refSchemaCtx.findModule(
                notificationDefinitionQName.getModule().getNamespace(),
                notificationDefinitionQName.getModule().getRevision()).orElse(null);
        requireNonNull(module, String.format("Module for namespace %s does not exist.",
                notificationDefinitionQName.getModule().getNamespace()));

        final StringBuilder streamNameBuilder = new StringBuilder();
        streamNameBuilder.append(RestconfStreamsConstants.NOTIFICATION_STREAM)
                .append('/')
                .append(module.getName())
                .append(':')
                .append(notificationDefinitionQName.getLocalName());
        if (outputType.equals(NotificationOutputType.JSON.getName())) {
            streamNameBuilder.append('/').append(NotificationOutputType.JSON.getName());
        }
        return streamNameBuilder.toString();
    }
}
