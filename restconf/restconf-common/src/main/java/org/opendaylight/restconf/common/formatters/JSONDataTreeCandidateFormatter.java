/*
 * Copyright (c) 2019 Pantheon Technologies, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.formatters;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import javax.xml.xpath.XPathExpressionException;
import org.opendaylight.yangtools.yang.data.api.schema.tree.DataTreeCandidate;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public final class JSONDataTreeCandidateFormatter extends DataTreeCandidateFormatter {
    private final JSONCodecFactorySupplier codecSupplier;

    private JSONDataTreeCandidateFormatter(final JSONCodecFactorySupplier codecSupplier) {
        this.codecSupplier = requireNonNull(codecSupplier);
    }

    private JSONDataTreeCandidateFormatter(final String xpathFilter, final JSONCodecFactorySupplier codecSupplier)
            throws XPathExpressionException {
        super(xpathFilter);
        this.codecSupplier = requireNonNull(codecSupplier);
    }

    public static EventFormatterFactory<Collection<DataTreeCandidate>> createFactory(
            final JSONCodecFactorySupplier codecSupplier) {
        requireNonNull(codecSupplier);
        return new EventFormatterFactory<Collection<DataTreeCandidate>>() {

            @Override
            public EventFormatter<Collection<DataTreeCandidate>> getFormatter(final String xpathFilter)
                    throws XPathExpressionException {
                return new JSONDataTreeCandidateFormatter(xpathFilter, codecSupplier);
            }

            @Override
            public EventFormatter<Collection<DataTreeCandidate>> getFormatter() {
                return new JSONDataTreeCandidateFormatter(codecSupplier);
            }
        };
    }

    @Override
    String createText(final SchemaContext schemaContext, final Collection<DataTreeCandidate> input, final Instant now)
            throws IOException {
//        final Writer writer = new StringWriter();
//        final JsonWriter jsonWriter = new JsonWriter(writer).beginObject();
//        jsonWriter.name("ietf-restconf:notification").beginObject();
//        writeNotificationBody(JSONNormalizedNodeStreamWriter.createNestedWriter(
//            codecSupplier.getShared(schemaContext), input.getType(), null, jsonWriter), input.getBody());
//        jsonWriter.endObject();
//
//        jsonWriter.name("event-time").value(toRFC3339(now)).endObject();
//        jsonWriter.close();
//        return writer.toString();

        return null;
    }
}
