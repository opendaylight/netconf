/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableBiMap;
import java.util.regex.Pattern;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeJson$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeXml$I;
import org.opendaylight.yangtools.yang.common.QName;

/**
 * An opinionated view on what values we can produce for {@code leaf encoding}. The name can only be composed
 * of one or more characters matching {@code [a-zA-Z]}.
 *
 * @param value Encoding name, as visible via the stream's {@code access} list's {@code encoding} leaf
 */
@NonNullByDefault
public record MonitoringEncoding(String value) {
    private static final Pattern PATTERN = Pattern.compile("[a-zA-Z]+");

    /**
     * Well-known JSON encoding defined by RFC8040's {@code ietf-restconf-monitoring.yang} as {@code json}.
     */
    public static final MonitoringEncoding JSON = new MonitoringEncoding("json");
    /**
     * Well-known XML encoding defined by RFC8040's {@code ietf-restconf-monitoring.yang} as {@code xml}.
     */
    public static final MonitoringEncoding XML = new MonitoringEncoding("xml");

    private static final ImmutableBiMap<QName, MonitoringEncoding> ENCODING_TO_MONITORING =
        ImmutableBiMap.of(EncodeJson$I.QNAME, JSON, EncodeXml$I.QNAME, XML);

    /**
     * Default constructor.
     *
     * @param value the encoding name
     */
    public MonitoringEncoding {
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("name must match " + PATTERN);
        }
    }

    /**
     * Factory method, taking into consideration well-known values.
     *
     * @param value the encoding name
     * @return A {@link MonitoringEncoding}
     * @throws IllegalArgumentException if the {@code name} is not a valid encoding name
     */
    public static MonitoringEncoding of(final String value) {
        return switch (value) {
            case "json" -> JSON;
            case "xml" -> XML;
            default -> new MonitoringEncoding(value);
        };
    }

    /**
     * Factory method for acquiring well-known values based on identities derived from
     * {@code ietf-subscribed-notifications.yang}'s {@code encoding} identity.
     *
     * @param encoding the encoding identity
     * @return a {@link MonitoringEncoding} or {@code null} if the encoding's {@code ietf-restconf-monitoring.yang}
     *         equivalent is not known.
     */
    public static @Nullable MonitoringEncoding forEncoding(final QName encoding) {
        return ENCODING_TO_MONITORING.get(requireNonNull(encoding));
    }

    /**
     * {@return the encoding identity associated with this encoding, or {@code null} if not known}
     */
    public @Nullable QName encoding() {
        // Note on the design here: we do not want leak this property into equality and this method should only be used
        // by legacy stream delivery, so this is just fine.
        return ENCODING_TO_MONITORING.inverse().get(this);
    }
}