/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions;

import java.util.Objects;
import org.opendaylight.yangtools.yang.common.QName;

public enum ErrorCode {
    /**
     * The publisher is unable to mark notification messages with prioritization information
     * in a way that will be respected during network transit.
     */
    DSCP_UNAVAILABLE("dscp-unavailable"),

    /**
     * Unable to encode notification messages in the desired format.
     */
    ENCODING_UNSUPPORTED("encoding-unsupported"),

    /**
     * Referenced filter does not exist.
     *
     * <p>
     * This means a receiver is referencing a filter that doesn't exist
     * or to which it does not have access permissions.
     */
    FILTER_UNAVAILABLE("filter-unavailable"),

    /**
     * Cannot parse syntax in the filter.
     *
     * <p>
     * This failure can be from a syntax error or a syntax too complex
     * to be processed by the publisher.
     */
    FILTER_UNSUPPORTED("filter-unsupported"),

    /**
     * The publisher does not have sufficient resources to support the requested subscription.
     *
     * <p>
     * An example might be that allocated CPU is too limited to generate
     * the desired set of notification messages.
     */
    INSUFFICIENT_RESOURCES("error-insufficient-resources"),

    /**
     * Referenced subscription doesn't exist.
     *
     * <p>
     * This may be as a result of a nonexistent subscription ID,
     * an ID that belongs to another subscriber, or an ID for a configured subscription.
     */
    NO_SUCH_SUBSCRIPTION("no-such-subscription"),

    /**
     * Replay cannot be performed for this subscription.
     *
     * <p>
     * This means the publisher will not provide the requested historic
     * information from the event stream via replay to this receiver.
     */
    REPLAY_UNSUPPORTED("replay-unsupported"),

    /**
     * Not a subscribable event stream.
     *
     * <p>
     * This means the referenced event stream is not available for subscription by the receiver.
     */
    STREAM_UNAVAILABLE("stream-unavailable"),

    /**
     * Termination of a previously suspended subscription.
     *
     * <p>
     * The publisher has eliminated the subscription, as it exceeded a time limit for suspension.
     */
    SUSPENSION_TIMEOUT("suspension-timeout"),

    /**
     * The publisher does not have the network bandwidth needed
     * to get the volume of generated information intended for a receiver.
     */
    UNSUPPORTABLE_VOLUME("unsupportable-volume");

    private static final String NS = "urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications";
    private static final String REV = "2019-09-09";
    private final QName codeQName;

    ErrorCode(final String name) {
        this.codeQName = QName.create(NS, REV, Objects.requireNonNull(name));
    }

    public QName getQName() {
        return codeQName;
    }
}
