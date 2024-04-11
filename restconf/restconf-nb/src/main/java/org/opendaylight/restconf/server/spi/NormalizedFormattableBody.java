/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.base.VerifyException;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.OutputStream;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.api.DatabindFormattableBody;
import org.opendaylight.restconf.server.api.DatabindPath.Data;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactory;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlCodecFactory;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

/**
 * A {@link FormattableBody} representing a data resource.
 */
@NonNullByDefault
public abstract sealed class NormalizedFormattableBody<N extends NormalizedNode> extends DatabindFormattableBody
        permits DataFormattableBody, RootFormattableBody {
    private final NormalizedNodeWriterFactory writerFactory;
    private final N data;

    NormalizedFormattableBody(final DatabindContext databind, final NormalizedNodeWriterFactory writerFactory,
            final N data) {
        super(databind);
        this.writerFactory = requireNonNull(writerFactory);
        this.data = requireNonNull(data);
    }

    public static NormalizedFormattableBody<?> of(final Data path, final NormalizedNode data,
            final NormalizedNodeWriterFactory writerFactory) {
        final var inference = path.inference();
        if (inference.isEmpty()) {
            // Read of the entire /data resource
            if (data instanceof ContainerNode container) {
                return new RootFormattableBody(path.databind(), writerFactory, container);
            }
            throw new VerifyException("Unexpected root data contract " + data.contract());
        }

        // Read of a sub-resource. We need to adjust the inference to point to the NormalizedNode parent of the node
        // being output.
        final Inference parentInference;
        if (data instanceof MapEntryNode || data instanceof LeafSetEntryNode || data instanceof UnkeyedListEntryNode) {
            parentInference = inference;
        } else {
            final var stack = inference.toSchemaInferenceStack();
            stack.exitToDataTree();
            parentInference = stack.toInference();
        }

        return new DataFormattableBody<>(path.databind(), parentInference, data, writerFactory);
    }

    /**
     * Return data.
     *
     * @return data
     */
    public final N data() {
        return data;
    }

    @Override
    protected final void formatToJSON(final DatabindContext databind, final PrettyPrintParam prettyPrint,
            final OutputStream out) throws IOException {
        try (var writer = FormattableBodySupport.createJsonWriter(out, prettyPrint)) {
            formatToJSON(databind.jsonCodecs(), data, writer);
        }
    }

    protected abstract void formatToJSON(JSONCodecFactory codecs, N data, JsonWriter writer) throws IOException;

    @Override
    protected final void formatToXML(final DatabindContext databind, final PrettyPrintParam prettyPrint,
            final OutputStream out) throws IOException {
        final var writer = FormattableBodySupport.createXmlWriter(out, prettyPrint);
        try {
            formatToXML(databind.xmlCodecs(), data, writer);
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Failed to write data", e);
        }
    }

    protected abstract void formatToXML(XmlCodecFactory codecs, N data, XMLStreamWriter writer)
        throws IOException, XMLStreamException;

    protected final NormalizedNodeWriter newWriter(final NormalizedNodeStreamWriter streamWriter) {
        return writerFactory.newWriter(streamWriter);
    }

    final void writeTo(final NormalizedNode toWrite, final NormalizedNodeStreamWriter streamWriter)
            throws IOException {
        try (var writer = newWriter(streamWriter)) {
            writer.write(toWrite);
        }
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("body", data.prettyTree());
    }


}
