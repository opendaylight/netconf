/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableSet;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

/**
 * {@link NotificationListenerAdapter} is responsible to track events on notifications.
 */
public final class NotificationListenerAdapter extends AbstractNotificationListenerAdaptor {
    private final ImmutableSet<QName> paths;

    /**
     * Set path of listener and stream name.
     *
     * @param paths      Top-level  Schema path of YANG notification.
     * @param streamName Name of the stream.
     * @param outputType Type of output on notification (JSON or XML).
     * @param listenersBroker Associated {@link ListenersBroker}
     */
    NotificationListenerAdapter(final ImmutableSet<QName> paths, final String streamName,
            final NotificationOutputType outputType, final ListenersBroker listenersBroker) {
        super(streamName, outputType, listenersBroker);
        this.paths = requireNonNull(paths);
    }

    @Override
    EffectiveModelContext effectiveModel() {
        return databindProvider.currentContext().modelContext();
    }

    /**
     * Return notification QNames.
     *
     * @return The YANG notification {@link QName}s this listener is bound to
     */
    public ImmutableSet<QName> qnames() {
        return paths;
    }

    public synchronized void listen(final DOMNotificationService notificationService) {
        if (!isListening()) {
            setRegistration(notificationService.registerNotificationListener(this,
                paths.stream().map(Absolute::of).toList()));
        }
    }

    @Override
    ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper.add("paths", paths));
    }
}
