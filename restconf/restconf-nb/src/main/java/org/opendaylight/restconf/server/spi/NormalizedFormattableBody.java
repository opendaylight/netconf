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
import java.io.IOException;
import java.io.OutputStream;
import javax.xml.stream.XMLStreamException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.FormatParameters;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.ParameterAwareNormalizedNodeWriter;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.api.DatabindFormattableBody;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

/**
 * A {@link FormattableBody} representing a data resource.
 */
@NonNullByDefault
public final class NormalizedFormattableBody<N extends NormalizedNode> extends DatabindFormattableBody {
    private final Inference parent;
    private final N data;

    public NormalizedFormattableBody(final FormatParameters format, final DatabindContext databind,
            final Inference parent, final N data) {
        super(format, databind);
        this.parent = requireNonNull(parent);
        this.data = requireNonNull(data);
    }

    @Override
    protected void formatToJSON(final OutputStream out, final FormatParameters format, final DatabindContext databind)
            throws IOException {
        try (var writer = ParameterAwareNormalizedNodeWriter.forStreamWriter(
                JSONNormalizedNodeStreamWriter.createExclusiveWriter(databind.jsonCodecs(), parent, null,
                    FormattableBodySupport.createJsonWriter(out, format)), null, null)) {
            writer.write(data());
        }
    }

    @Override
    protected void formatToXML(final OutputStream out, final FormatParameters format, final DatabindContext databind)
            throws IOException {
        final var xmlWriter = FormattableBodySupport.createXmlWriter(out, format);
        try (var nnWriter = ParameterAwareNormalizedNodeWriter.forStreamWriter(
            XMLStreamNormalizedNodeStreamWriter.create(xmlWriter, parent), null, null)) {
            nnWriter.write(data());
        }

        try {
            xmlWriter.close();
        } catch (XMLStreamException e) {
            throw new IOException("Failed to write data", e);
        }
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper.add("body", data.prettyTree()));
    }

    private NormalizedNode data() {
        // RESTCONF allows returning one list item. We need to wrap it in map node in order to serialize it properly
        return data instanceof MapEntryNode mapEntry
            ? ImmutableNodes.newSystemMapBuilder()
                .withNodeIdentifier(new NodeIdentifier(data.name().getNodeType()))
                .withChild(mapEntry)
                .build()
            : data;
    }
}
