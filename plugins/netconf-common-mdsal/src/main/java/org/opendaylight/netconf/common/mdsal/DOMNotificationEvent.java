/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.common.mdsal;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import java.time.Instant;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.dom.api.DOMEvent;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.api.messages.NotificationMessage;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

/**
 * A baseline {@link DOMNotification} which is also a {@link DOMEvent}. This is equivalent to a
 * {@link NotificationMessage}.
 */
@Beta
@NonNullByDefault
public abstract sealed class DOMNotificationEvent implements DOMEvent, DOMNotification {
    /**
     * An old-school global YANG 1.0 {@code notification}, as defined in RFC6020.
     */
    public static final class Rfc6020 extends DOMNotificationEvent {
        public Rfc6020(final ContainerNode body, final Instant eventTime) {
            super(body, eventTime);
        }

        @Override
        public Absolute getType() {
            return Absolute.of(getBody().name().getNodeType());
        }
    }

    /**
     * A YANG 1.1 instance {@code notification}, as defined in RFC7950. It carries the {@link #path()} of the datastore
     * instance for which it was generated.
     */
    public static final class Rfc7950 extends DOMNotificationEvent {
        private final Absolute type;
        private final YangInstanceIdentifier path;

        public Rfc7950(final Absolute type, final YangInstanceIdentifier path, final ContainerNode body,
                final Instant eventTime) {
            super(body, eventTime);
            this.type = requireNonNull(type);
            this.path = requireNonNull(path);
            if (path.isEmpty()) {
                throw new IllegalArgumentException("Path must not be empty");
            }
        }

        @Override
        public Absolute getType() {
            return type;
        }

        public YangInstanceIdentifier path() {
            return path;
        }

        @Override
        ToStringHelper addToStringAttributes(final ToStringHelper helper) {
            return super.addToStringAttributes(helper).add("path", path);
        }
    }

    private final ContainerNode body;
    private final Instant eventTime;

    DOMNotificationEvent(final ContainerNode body, final Instant eventTime) {
        this.body = requireNonNull(body);
        this.eventTime = requireNonNull(eventTime);
    }

    @Override
    public final ContainerNode getBody() {
        return body;
    }

    @Override
    public final Instant getEventInstant() {
        return eventTime;
    }

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(DOMNotificationEvent.class))
            .add("body", body.prettyTree())
            .toString();
    }

    ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("type", getType()).add("eventTime", eventTime);
    }
}
