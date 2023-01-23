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

    private JSONDataTreeCandidateFormatter(final JSONCodecFactorySupplier codecSupplier) {
        this.codecSupplier = requireNonNull(codecSupplier);
    }

    private JSONDataTreeCandidateFormatter(final String xpathFilter, final JSONCodecFactorySupplier codecSupplier)
            throws XPathExpressionException {
        super(xpathFilter);
        this.codecSupplier = requireNonNull(codecSupplier);
    }

    public static DataTreeCandidateFormatterFactory createFactory(
            final JSONCodecFactorySupplier codecSupplier) {
        requireNonNull(codecSupplier);
        return new DataTreeCandidateFormatterFactory() {
            @Override
            public DataTreeCandidateFormatter getFormatter(final String xpathFilter)
                    throws XPathExpressionException {
                return new JSONDataTreeCandidateFormatter(xpathFilter, codecSupplier);
            }

            @Override
            public DataTreeCandidateFormatter getFormatter() {
                return new JSONDataTreeCandidateFormatter(codecSupplier);
            }
        };
    }

    @Override
    String createText(final EffectiveModelContext schemaContext, final Collection<DataTreeCandidate> input,
                      final Instant now, final boolean leafNodesOnly, final boolean skipData,
                      final boolean changedLeafNodesOnly, final String deviceId)
            throws IOException {
        final Writer writer = new StringWriter();
        final JsonWriter jsonWriter = new JsonWriter(writer).beginObject();

        jsonWriter.name(NETCONF_NOTIFICATION_NAMESPACE + ":notification").beginObject();
        jsonWriter.name(SAL_REMOTE_NAMESPACE + ":data-changed-notification").beginObject();
        jsonWriter.name("data-change-event").beginArray();

        final var serializer = new JsonDataTreeCandidateSerializer(schemaContext, codecSupplier, jsonWriter);
        boolean nonEmpty = false;
        for (var candidate : input) {
            nonEmpty |= serializer.serialize(candidate, leafNodesOnly, skipData, changedLeafNodesOnly);
        }

        // data-change-event
        jsonWriter.endArray();
        // data-changed-notification
        jsonWriter.endObject();

        jsonWriter.name("event-time").value(toRFC3339(now));

        if (deviceId != null) {
            jsonWriter.name("node-id").value(deviceId);
        }
        jsonWriter.endObject();

        // notification
        jsonWriter.endObject();

        return nonEmpty ? writer.toString() : null;
    }
}
