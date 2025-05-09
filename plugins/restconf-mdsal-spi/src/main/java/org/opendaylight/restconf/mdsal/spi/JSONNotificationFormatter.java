/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.mdsal.spi;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.restconf.server.spi.TextParameters;
import org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.YangModuleInfoImpl;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

final class JSONNotificationFormatter extends NotificationFormatter {
    private static final @NonNull String NOTIFICATION_NAME =
        YangModuleInfoImpl.getInstance().getName().getLocalName() + ":notification";
    @VisibleForTesting
    static final JSONNotificationFormatter EMPTY = new JSONNotificationFormatter(TextParameters.EMPTY);

    static final NotificationFormatterFactory FACTORY = new NotificationFormatterFactory(EMPTY) {
        @Override
        public JSONNotificationFormatter newFormatter(final TextParameters textParams) {
            return new JSONNotificationFormatter(textParams);
        }
    };

    private JSONNotificationFormatter(final TextParameters textParams) {
        super(textParams);
    }

    @Override
    protected String createText(final TextParameters params, final EffectiveModelContext schemaContext,
            final DOMNotification input, final Instant now) throws IOException {
        try (var writer = new StringWriter()) {
            try (var jsonWriter = new JsonWriter(writer)) {
                jsonWriter.beginObject()
                    .name(NOTIFICATION_NAME).beginObject()
                        .name("event-time").value(toRFC3339(now));
                writeBody(JSONNormalizedNodeStreamWriter.createNestedWriter(
                    JSONCodecFactorySupplier.RFC7951.getShared(schemaContext), input.getType(), null, jsonWriter),
                    input.getBody());
                jsonWriter.endObject().endObject();
            }
            return writer.toString();
        }
    }
}
