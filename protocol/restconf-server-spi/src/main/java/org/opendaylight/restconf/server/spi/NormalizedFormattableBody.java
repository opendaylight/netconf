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
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.netconf.databind.DatabindPath.OperationPath;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.FormattableBodySupport;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactory;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlCodecFactory;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

/**
 * A {@link FormattableBody} representing a data resource.
 */
@NonNullByDefault
public abstract sealed class NormalizedFormattableBody<N extends NormalizedNode> extends FormattableBody
        permits DataFormattableBody, RootFormattableBody {
    private final NormalizedNodeWriterFactory writerFactory;
    private final DatabindContext databind;
    private final N data;

    NormalizedFormattableBody(final DatabindContext databind, final NormalizedNodeWriterFactory writerFactory,
            final N data) {
        this.databind = requireNonNull(databind);
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

        // RESTCONF allows returning one list item. We need to wrap it in MapNode/LeafSetNode node in order
        // to serialize it properly. We need to point to a 'a parent inference' and provide an appropriate data entry.
        // Unfortunately it is not quite defined what that actually means.
        //
        // This is a tricky thing, as JSON and XML have different representations of a MapEntryNode/LeafSetEntryNode.
        // In JSON it is the array containing individual objects. In XML it is transparent.
        //
        // This means that for XML we could just move 'parent inference' to the MapNode and emit the MapEntryNode as
        // usual. For JSON that does not work, as we also need to wrap the MapEntryNode in a MapNode.
        //
        // What we do here is we unconditionally:
        //   - wrap the node if it is a list entry node
        //   - move the inference to parent
        //
        // For XML that does not seem to matter. For JSON it does matter a lot.
        final var stack = inference.toSchemaInferenceStack();
        stack.exit();

        return new DataFormattableBody<>(path.databind(), stack.toInference(), wrapListEntryNodes(data), writerFactory);
    }

    /**
     * Return a {@link FormattableBody} corresponding to a {@code rpc} or {@code action} invocation.
     *
     * @param path invocation path
     * @param data the data
     */
    public static NormalizedFormattableBody<ContainerNode> of(final OperationPath path,
            final ContainerNode data) {
        return new DataFormattableBody<>(path.databind(), path.inference(), data);
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
    public final void formatToJSON(final PrettyPrintParam prettyPrint, final OutputStream out) throws IOException {
        try (var writer = FormattableBodySupport.createJsonWriter(out, prettyPrint)) {
            formatToJSON(databind.jsonCodecs(), data, writer);
        }
    }

    protected abstract void formatToJSON(JSONCodecFactory codecs, N data, JsonWriter writer) throws IOException;

    @Override
    public final void formatToXML(final PrettyPrintParam prettyPrint, final OutputStream out) throws IOException {
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

    /**
     * Wrap the NormalizedNode with a parent node if it is a MapEntryNode or LeafSetEntryNode.
     *
     * @param data data
     * @return {@link NormalizedNode}
     */
    private static NormalizedNode wrapListEntryNodes(final NormalizedNode data) {
        if (data instanceof MapEntryNode mapEntry) {
            return ImmutableNodes.newSystemMapBuilder()
                .withNodeIdentifier(new NodeIdentifier(data.name().getNodeType()))
                .withChild(mapEntry)
                .build();
        } if (data instanceof LeafSetEntryNode leafSetNode) {
            return ImmutableNodes.newSystemLeafSetBuilder()
                .withNodeIdentifier(new NodeIdentifier(data.name().getNodeType()))
                .withChild(leafSetNode)
                .build();
        } else {
            return data;
        }
    }
}
