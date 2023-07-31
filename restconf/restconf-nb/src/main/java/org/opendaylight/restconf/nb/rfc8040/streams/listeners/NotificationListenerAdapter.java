/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

/**
 * {@link NotificationListenerAdapter} is responsible to track events on notifications.
 */
public final class NotificationListenerAdapter extends AbstractNotificationListenerAdaptor {
    private final Absolute path;

    /**
     * Set path of listener and stream name.
     *
     * @param path       Schema path of YANG notification.
     * @param streamName Name of the stream.
     * @param outputType Type of output on notification (JSON or XML).
     */
    NotificationListenerAdapter(final Absolute path, final String streamName, final NotificationOutputType outputType) {
        super(streamName, outputType);
        this.path = requireNonNull(path);
    }

    @Override
    EffectiveModelContext effectiveModel() {
        return databindProvider.currentContext().modelContext();
    }

    /**
     * Get schema path of notification.
     *
     * @return The configured schema path that points to observing YANG notification schema node.
     */
    public Absolute getSchemaPath() {
        return path;
    }

    public synchronized void listen(final DOMNotificationService notificationService) {
        if (!isListening()) {
            setRegistration(notificationService.registerNotificationListener(this, getSchemaPath()));
        }
    }

    @Override
    ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper.add("path", path));
    }
}
