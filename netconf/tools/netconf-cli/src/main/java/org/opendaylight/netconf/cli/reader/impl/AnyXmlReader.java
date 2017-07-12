/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.cli.reader.impl;

import static org.opendaylight.netconf.cli.io.IOUtil.isSkipInput;
import static org.opendaylight.netconf.cli.io.IOUtil.listType;

import com.google.common.base.Optional;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.netconf.cli.io.BaseConsoleContext;
import org.opendaylight.netconf.cli.io.ConsoleContext;
import org.opendaylight.netconf.cli.io.ConsoleIO;
import org.opendaylight.netconf.cli.reader.AbstractReader;
import org.opendaylight.netconf.cli.reader.ReadingException;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableLeafNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.AnyXmlSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class AnyXmlReader extends AbstractReader<AnyXmlSchemaNode> {

    public AnyXmlReader(final ConsoleIO console, final SchemaContext schemaContext) {
        super(console, schemaContext);
    }

    public AnyXmlReader(final ConsoleIO console, final SchemaContext schemaContext, final boolean readConfigNode) {
        super(console, schemaContext, readConfigNode);
    }

    @Override
    protected List<NormalizedNode<?, ?>> readWithContext(final AnyXmlSchemaNode schemaNode)
            throws IOException, ReadingException {
        console.writeLn(listType(schemaNode) + " " + schemaNode.getQName().getLocalName());

        final String rawValue = console.read();

        DataContainerChild<?, ?> newNode = null;
        if (!isSkipInput(rawValue)) {
            final Optional<DataContainerChild<?, ?>> value = tryParse(rawValue, schemaNode);

            if (value.isPresent()) {
                newNode = ImmutableContainerNodeBuilder.create()
                        .withNodeIdentifier(new NodeIdentifier(schemaNode.getQName()))
                        .withChild(value.get()).build();
            } else {
                newNode = ImmutableLeafNodeBuilder.create().withNodeIdentifier(
                    new NodeIdentifier(schemaNode.getQName())).withValue(rawValue).build();
            }
        }

        final List<NormalizedNode<?, ?>> newNodes = new ArrayList<>();
        newNodes.add(newNode);
        return newNodes;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private Optional<DataContainerChild<?, ?>> tryParse(final String rawValue, final AnyXmlSchemaNode schemaNode) {
        try {
            final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
            final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
            final XmlParserStream xmlParser = XmlParserStream.create(writer, getSchemaContext(), schemaNode);

            xmlParser.parse(UntrustedXML.createXMLStreamReader(new StringReader(rawValue)));
            return Optional.of((DataContainerChild<?, ?>) resultHolder.getResult());
        } catch (final Exception e) {
            // TODO log
            return Optional.absent();
        }
    }

    @Override
    protected ConsoleContext getContext(final AnyXmlSchemaNode schemaNode) {
        return new BaseConsoleContext<>(schemaNode);
    }
}
