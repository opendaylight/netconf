/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static java.util.Objects.requireNonNull;

import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import javax.xml.xpath.XPathExpressionException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.$YangModuleInfoImpl;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

final class JSONNotificationFormatter extends NotificationFormatter {
    private static final @NonNull String NOTIFICATION_NAME;

    static {
        final var ietfRestconfName = $YangModuleInfoImpl.getInstance().getName();
        NOTIFICATION_NAME = ietfRestconfName.getLocalName() + ":notification";
    }

    private final JSONCodecFactorySupplier codecSupplier;

    private JSONNotificationFormatter(final TextParameters textParams, final JSONCodecFactorySupplier codecSupplier) {
        super(textParams);
        this.codecSupplier = requireNonNull(codecSupplier);
    }

    private JSONNotificationFormatter(final TextParameters textParams, final String xpathFilter,
            final JSONCodecFactorySupplier codecSupplier) throws XPathExpressionException {
        super(textParams, xpathFilter);
        this.codecSupplier = requireNonNull(codecSupplier);
    }

    static NotificationFormatterFactory createFactory(final JSONCodecFactorySupplier codecSupplier) {
        final var empty = new JSONNotificationFormatter(TextParameters.EMPTY, codecSupplier);
        return new NotificationFormatterFactory(empty) {
            @Override
            JSONNotificationFormatter getFormatter(final TextParameters textParams, final String xpathFilter)
                    throws XPathExpressionException {
                return new JSONNotificationFormatter(textParams, xpathFilter, codecSupplier);
            }

            @Override
            JSONNotificationFormatter newFormatter(final TextParameters textParams) {
                return new JSONNotificationFormatter(textParams, codecSupplier);
            }
        };
    }

    @Override
    String createText(final TextParameters params, final EffectiveModelContext schemaContext,
            final DOMNotification input, final Instant now) throws IOException {
        try (var writer = new StringWriter()) {
            try (var jsonWriter = new JsonWriter(writer)) {
                jsonWriter.beginObject()
                    .name(NOTIFICATION_NAME).beginObject()
                        .name("event-time").value(toRFC3339(now));
                writeNotificationBody(JSONNormalizedNodeStreamWriter.createNestedWriter(
                    codecSupplier.getShared(schemaContext), input.getType(), null, jsonWriter), input.getBody());
                jsonWriter.endObject().endObject();
            }
            return writer.toString();
        }
    }
}
