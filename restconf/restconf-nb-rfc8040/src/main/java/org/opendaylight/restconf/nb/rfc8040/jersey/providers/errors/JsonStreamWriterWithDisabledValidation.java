/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.jersey.providers.errors;

import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonWriterFactory;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

/**
 * JSON stream-writer with disabled leaf-type validation for specified QName.
 */
final class JsonStreamWriterWithDisabledValidation extends StreamWriterWithDisabledValidation {

    private final JsonWriter jsonWriter;
    private final NormalizedNodeStreamWriter jsonNodeStreamWriter;

    /**
     * Creation of the custom JSON stream-writer.
     *
     * @param excludedQName        QName of the element that is excluded from type-check.
     * @param outputWriter         Output stream that is used for creation of JSON writers.
     * @param schemaPath           Schema-path of the {@link NormalizedNode} to be written.
     * @param initialNs            Initial namespace derived from schema node of the data that are serialized.
     * @param schemaContextHandler Handler that holds actual schema context.
     */
    JsonStreamWriterWithDisabledValidation(final QName excludedQName, final OutputStreamWriter outputWriter,
            final SchemaPath schemaPath, final URI initialNs, final SchemaContextHandler schemaContextHandler) {
        super(excludedQName);
        this.jsonWriter = JsonWriterFactory.createJsonWriter(outputWriter);
        this.jsonNodeStreamWriter = JSONNormalizedNodeStreamWriter.createExclusiveWriter(
                JSONCodecFactorySupplier.DRAFT_LHOTKA_NETMOD_YANG_JSON_02.getShared(schemaContextHandler.get()),
                schemaPath, initialNs, jsonWriter);
    }

    @Override
    protected NormalizedNodeStreamWriter delegate() {
        return jsonNodeStreamWriter;
    }

    @Override
    void startLeafNodeWithDisabledValidation(final NodeIdentifier nodeIdentifier) throws IOException {
        jsonWriter.name(nodeIdentifier.getNodeType().getLocalName());
    }

    @Override
    void scalarValueWithDisabledValidation(final Object value) throws IOException {
        jsonWriter.value(value.toString());
    }

    @Override
    void endNodeWithDisabledValidation() {
        // nope
    }

    @Override
    public void close() throws IOException {
        jsonNodeStreamWriter.close();
        jsonWriter.close();
    }
}