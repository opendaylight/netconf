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
import java.util.Collection;
import javax.xml.xpath.XPathExpressionException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.$YangModuleInfoImpl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.DataChangedNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.data.changed.notification.DataChangeEvent;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidate;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public final class JSONDataTreeCandidateFormatter extends DataTreeCandidateFormatter {
    private static final @NonNull String DATA_CHANGED_EVENT_NAME = DataChangeEvent.QNAME.getLocalName();
    private static final @NonNull String DATA_CHANGED_NOTIFICATION_NAME =
        $YangModuleInfoImpl.getInstance().getName().getLocalName() + ":" + DataChangedNotification.QNAME.getLocalName();

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
        try (var writer = new StringWriter()) {
            boolean nonEmpty = false;
            try (var jsonWriter = new JsonWriter(writer)) {
                jsonWriter.beginObject()
                    .name("ietf-restconf:notification").beginObject()
                        .name("event-time").value(toRFC3339(now))
                        .name(DATA_CHANGED_NOTIFICATION_NAME).beginObject()
                            .name(DATA_CHANGED_EVENT_NAME).beginArray();

                final var serializer = new JsonDataTreeCandidateSerializer(schemaContext, codecSupplier, jsonWriter);
                for (var candidate : input) {
                    nonEmpty |= serializer.serialize(candidate, params);
                }

                jsonWriter.endArray().endObject().endObject().endObject();
            }

            return nonEmpty ? writer.toString() : null;
        }
    }
}
