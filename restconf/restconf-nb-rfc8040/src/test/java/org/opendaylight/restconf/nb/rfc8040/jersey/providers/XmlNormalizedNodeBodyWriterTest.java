/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import javax.ws.rs.core.MediaType;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class XmlNormalizedNodeBodyWriterTest {

    private static final String EMPTY_OUTPUT = "<data xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"></data>";

    private static EffectiveModelContext schemaContext;

    @BeforeClass
    public static void initialization() throws Exception {
        schemaContext = Mockito.mock(EffectiveModelContext.class);
    }

    @Test
    public void testWriteEmptyRootContainer() throws Exception {
        final SchemaNode schemaNode = Mockito.mock(SchemaNode.class);
        when(schemaNode.getPath()).thenReturn(SchemaPath.ROOT);

        final InstanceIdentifierContext<SchemaNode> identifierContext =
                new InstanceIdentifierContext<>(YangInstanceIdentifier.empty(), schemaNode, null, schemaContext);
        final ContainerNode data = ImmutableContainerNodeBuilder.create()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(SchemaContext.NAME)).build();
        final NormalizedNodePayload nodePayload = NormalizedNodePayload.of(identifierContext, data);

        final OutputStream output = new ByteArrayOutputStream();
        final XmlNormalizedNodeBodyWriter xmlWriter = new XmlNormalizedNodeBodyWriter();
        xmlWriter.writeTo(nodePayload, null, null, null, MediaType.APPLICATION_XML_TYPE, null, output);

        assertEquals(EMPTY_OUTPUT, output.toString());
    }
}