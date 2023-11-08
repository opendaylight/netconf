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
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev231103.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

/**
 * A {@link RestconfStream} reporting YANG notifications.
 */
public final class NotificationStream extends AbstractNotificationStream {
    private final DatabindProvider databindProvider;
    private final ImmutableSet<QName> paths;

    public NotificationStream(final ListenersBroker listenersBroker, final String name,
            final NotificationOutputType outputType, final DatabindProvider databindProvider,
            final ImmutableSet<QName> paths) {
        super(listenersBroker, name, outputType);
        this.databindProvider = requireNonNull(databindProvider);
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
