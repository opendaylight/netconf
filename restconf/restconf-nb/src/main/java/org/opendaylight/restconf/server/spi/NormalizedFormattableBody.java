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

import com.google.common.base.MoreObjects.ToStringHelper;
import java.io.IOException;
import java.io.OutputStream;
import javax.xml.stream.XMLStreamException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.api.DatabindFormattableBody;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.api.stmt.DataTreeEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.LeafListEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.ListEffectiveStatement;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

/**
 * A {@link FormattableBody} representing a data resource.
 */
@NonNullByDefault
public final class NormalizedFormattableBody<N extends NormalizedNode> extends DatabindFormattableBody {
    private final Inference parent;
    private final N data;

    public NormalizedFormattableBody(final DatabindContext databind, final Inference parent, final N data) {
        super(databind);
        this.parent = requireNonNull(parent);
        this.data = requireNonNull(data);
        // RESTCONF allows returning one list item. We need to wrap it in map node in order to serialize it properly,
        // which is where the notion of "parent Inference" may be confusing. We mean
        // 'inference to the parent NormalizedNode', which for *EntryNode ends up the corresponding statement
        if (data instanceof MapEntryNode) {
            verifyParent(parent, ListEffectiveStatement.class, data);
        } else if (data instanceof LeafSetEntryNode) {
            verifyParent(parent, LeafListEffectiveStatement.class, data);
        }
    }

    private static void verifyParent(final Inference inference,
            final Class<? extends DataTreeEffectiveStatement<?>> expectedStmt, final NormalizedNode data) {
        // Let's not bother with niceties of error report -- if we trip here, the caller is doing the wrong
        final var qname = expectedStmt.cast(inference.toSchemaInferenceStack().currentStatement()).argument();
        verify(qname.equals(data.name().getNodeType()));
    }

    @Override
    protected void formatToJSON(final DatabindContext databind, final PrettyPrintParam prettyPrint,
            final OutputStream out) throws IOException {
        writeTo(JSONNormalizedNodeStreamWriter.createExclusiveWriter(databind.jsonCodecs(), parent, null,
            FormattableBodySupport.createJsonWriter(out, prettyPrint)));
    }

    @Override
    protected void formatToXML(final DatabindContext databind, final PrettyPrintParam prettyPrint,
            final OutputStream out) throws IOException {
        final var xmlWriter = FormattableBodySupport.createXmlWriter(out, prettyPrint);
        writeTo(XMLStreamNormalizedNodeStreamWriter.create(xmlWriter, parent));
        try {
            xmlWriter.close();
        } catch (XMLStreamException e) {
            throw new IOException("Failed to write data", e);
        }
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("body", data.prettyTree());
    }

    private void writeTo(final NormalizedNodeStreamWriter streamWriter) throws IOException {
        try (var writer = NormalizedNodeWriter.forStreamWriter(streamWriter)) {
            writer.write(data);
        }
    }
}
