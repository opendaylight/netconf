/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal.streams.notif;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableSet;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.server.spi.RestconfStream.Sink;
import org.opendaylight.restconf.server.spi.RestconfStream.Source;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

/**
 * A {@link Source} reporting YANG notifications.
 */
final class NotificationSource extends AbstractNotificationSource {
    private final DatabindProvider databindProvider;
    private final DOMNotificationService notificationService;
    private final ImmutableSet<QName> qnames;

    NotificationSource(final DatabindProvider databindProvider, final DOMNotificationService notificationService,
            final ImmutableSet<QName> qnames) {
        this.databindProvider = requireNonNull(databindProvider);
        this.notificationService = requireNonNull(notificationService);
        this.qnames = requireNonNull(qnames);
    }

    @Override
    protected Registration start(final Sink<DOMNotification> sink) {
        return notificationService.registerNotificationListener(
            new Listener(sink, () -> databindProvider.currentContext().modelContext()),
            qnames.stream().map(Absolute::of).toList());
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper.add("qnames", qnames));
    }
}
