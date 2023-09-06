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
import java.util.Collection;
import javax.xml.xpath.XPathExpressionException;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidate;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public final class JSONDataTreeCandidateFormatter extends DataTreeCandidateFormatter {
    public static final String SAL_REMOTE_NAMESPACE = "urn-opendaylight-params-xml-ns-yang-controller-md-sal-remote";
    public static final String NETCONF_NOTIFICATION_NAMESPACE = "urn-ietf-params-xml-ns-netconf-notification-1.0";
    private final JSONCodecFactorySupplier codecSupplier;

    private JSONDataTreeCandidateFormatter(final TextParameters textParams,
            final JSONCodecFactorySupplier codecSupplier) {
        super(textParams);
        this.codecSupplier = requireNonNull(codecSupplier);
    }

    private JSONDataTreeCandidateFormatter(final TextParameters textParams, final String xpathFilter,
            final JSONCodecFactorySupplier codecSupplier) throws XPathExpressionException {
        super(textParams, xpathFilter);
        this.codecSupplier = requireNonNull(codecSupplier);
    }

    public static DataTreeCandidateFormatterFactory createFactory(
            final JSONCodecFactorySupplier codecSupplier) {
        final var empty = new JSONDataTreeCandidateFormatter(TextParameters.EMPTY, codecSupplier);
        return new DataTreeCandidateFormatterFactory(empty) {
            @Override
            DataTreeCandidateFormatter newFormatter(final TextParameters textParams) {
                return new JSONDataTreeCandidateFormatter(textParams, codecSupplier);
            }

            @Override
            DataTreeCandidateFormatter getFormatter(final TextParameters textParams, final String xpathFilter)
                    throws XPathExpressionException {
                return new JSONDataTreeCandidateFormatter(textParams, xpathFilter, codecSupplier);
            }
        };
    }

    @Override
    String createText(final TextParameters params, final EffectiveModelContext schemaContext,
            final Collection<DataTreeCandidate> input, final Instant now) throws IOException {
        final Writer writer = new StringWriter();
        final JsonWriter jsonWriter = new JsonWriter(writer).beginObject();

        jsonWriter.name(NETCONF_NOTIFICATION_NAMESPACE + ":notification").beginObject();
        jsonWriter.name(SAL_REMOTE_NAMESPACE + ":data-changed-notification").beginObject();
        jsonWriter.name("data-change-event").beginArray();

        final var serializer = new JsonDataTreeCandidateSerializer(schemaContext, codecSupplier, jsonWriter);
        boolean nonEmpty = false;
        for (var candidate : input) {
            nonEmpty |= serializer.serialize(candidate, params);
        }

        // data-change-event
        jsonWriter.endArray();
        // data-changed-notification
        jsonWriter.endObject();

        jsonWriter.name("event-time").value(toRFC3339(now));
        jsonWriter.endObject();

        // notification
        jsonWriter.endObject();

        return nonEmpty ? writer.toString() : null;
    }
}
