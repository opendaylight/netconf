/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static com.google.common.base.Verify.verify;
import static java.util.Objects.requireNonNull;

import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import javax.xml.stream.XMLStreamWriter;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.common.DatabindContext;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactory;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlCodecFactory;
import org.opendaylight.yangtools.yang.model.api.stmt.DataTreeEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.LeafListEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.ListEffectiveStatement;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

/**
 * A {@link NormalizedFormattableBody} representing a data resource.
 */
@NonNullByDefault
final class DataFormattableBody<N extends NormalizedNode> extends NormalizedFormattableBody<N> {
    private final Inference parent;

    DataFormattableBody(final DatabindContext databind, final Inference parent, final N data,
            final NormalizedNodeWriterFactory writerFactory) {
        super(databind, writerFactory, data);
        this.parent = requireNonNull(parent);

        // RESTCONF allows returning one list item. We need to wrap it in map node in order to serialize it properly,
        // which is where the notion of "parent Inference" may be confusing. We mean
        // 'inference to the parent NormalizedNode', which for *EntryNode ends up the corresponding statement
        if (data instanceof MapEntryNode) {
            verifyParent(parent, ListEffectiveStatement.class, data);
        } else if (data instanceof LeafSetEntryNode) {
            verifyParent(parent, LeafListEffectiveStatement.class, data);
        }
    }

    DataFormattableBody(final DatabindContext databind, final Inference parent, final N data) {
        this(databind, parent, data, NormalizedNodeWriterFactory.of());
    }

    private static void verifyParent(final Inference inference,
            final Class<? extends DataTreeEffectiveStatement<?>> expectedStmt, final NormalizedNode data) {
        // Let's not bother with niceties of error report -- if we trip here, the caller is doing the wrong
        final var qname = expectedStmt.cast(inference.toSchemaInferenceStack().currentStatement()).argument();
        verify(qname.equals(data.name().getNodeType()));
    }

    @Override
    protected void formatToJSON(final JSONCodecFactory codecs, final N data, final JsonWriter writer)
            throws IOException {
        writeTo(data, JSONNormalizedNodeStreamWriter.createExclusiveWriter(codecs, parent, null, writer));
    }

    @Override
    protected void formatToXML(final XmlCodecFactory codecs, final N data, final XMLStreamWriter xmlWriter)
            throws IOException {
        writeTo(data, XMLStreamNormalizedNodeStreamWriter.create(xmlWriter, parent));
    }
}
