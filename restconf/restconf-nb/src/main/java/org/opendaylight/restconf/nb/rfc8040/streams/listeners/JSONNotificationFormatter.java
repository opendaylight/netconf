/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import static java.util.Objects.requireNonNull;

import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.time.Instant;
import javax.xml.xpath.XPathExpressionException;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

final class JSONNotificationFormatter extends NotificationFormatter {
    private final JSONCodecFactorySupplier codecSupplier;

    private JSONNotificationFormatter(final JSONCodecFactorySupplier codecSupplier) {
        this.codecSupplier = requireNonNull(codecSupplier);
    }

    private JSONNotificationFormatter(final String xpathFilter, final JSONCodecFactorySupplier codecSupplier)
            throws XPathExpressionException {
        super(xpathFilter);
        this.codecSupplier = requireNonNull(codecSupplier);
    }

    static NotificationFormatterFactory createFactory(final JSONCodecFactorySupplier codecSupplier) {
        requireNonNull(codecSupplier);
        return new NotificationFormatterFactory() {
            @Override
            public JSONNotificationFormatter getFormatter(final String xpathFilter)
                    throws XPathExpressionException {
                return new JSONNotificationFormatter(xpathFilter, codecSupplier);
            }

            @Override
            public JSONNotificationFormatter getFormatter() {
                return new JSONNotificationFormatter(codecSupplier);
            }
        };
    }

    @Override
    String createText(final EffectiveModelContext schemaContext, final DOMNotification input, final Instant now,
                      final boolean leafNodesOnly, final boolean skipData, final boolean changedLeafNodesOnly,
                      final String deviceId)
            throws IOException {
        final Writer writer = new StringWriter();
        final JsonWriter jsonWriter = new JsonWriter(writer).beginObject();
        jsonWriter.name("ietf-restconf:notification").beginObject();
        writeNotificationBody(JSONNormalizedNodeStreamWriter.createNestedWriter(
                codecSupplier.getShared(schemaContext), input.getType(), null, jsonWriter), input.getBody());
        jsonWriter.endObject();
        jsonWriter.name("event-time").value(toRFC3339(now));

        if (deviceId != null) {
            jsonWriter.name("node-id").value(deviceId).endObject();
        } else {
            jsonWriter.endObject();
        }
        jsonWriter.close();
        return writer.toString();
    }
}
