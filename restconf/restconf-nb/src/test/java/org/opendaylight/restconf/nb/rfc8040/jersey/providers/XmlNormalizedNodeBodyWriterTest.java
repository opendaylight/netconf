/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.ws.rs.core.MediaType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class XmlNormalizedNodeBodyWriterTest {
    @Test
    public void testWriteEmptyRootContainer() throws IOException {
        final EffectiveModelContext schemaContext = mock(EffectiveModelContext.class);

        final NormalizedNodePayload nodePayload = NormalizedNodePayload.of(
            InstanceIdentifierContext.ofLocalRoot(schemaContext),
            Builders.containerBuilder().withNodeIdentifier(new NodeIdentifier(SchemaContext.NAME)).build());

        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final XmlNormalizedNodeBodyWriter xmlWriter = new XmlNormalizedNodeBodyWriter();
        xmlWriter.writeTo(nodePayload, null, null, null, MediaType.APPLICATION_XML_TYPE, null, output);

        assertEquals("<data xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"></data>",
            output.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void testRootContainerWrite() throws IOException {
        final EffectiveModelContext schemaContext =
                TestRestconfUtils.loadSchemaContext("/instanceidentifier/yang", null);

        final NormalizedNodePayload nodePayload = NormalizedNodePayload.of(
            InstanceIdentifierContext.ofLocalRoot(schemaContext),
            Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(SchemaContext.NAME))
                .withChild(Builders.containerBuilder()
                    .withNodeIdentifier(new NodeIdentifier(
                        QName.create("foo:module", "2016-09-29", "foo-bar-container")))
                    .build())
                .withChild(Builders.containerBuilder()
                    .withNodeIdentifier(new NodeIdentifier(
                        QName.create("bar:module", "2016-09-29", "foo-bar-container")))
                    .build())
                .build());

        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final XmlNormalizedNodeBodyWriter xmlWriter = new XmlNormalizedNodeBodyWriter();
        xmlWriter.writeTo(nodePayload, null, null, null, MediaType.APPLICATION_XML_TYPE, null, output);

        assertEquals("<data xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">"
            + "<foo-bar-container xmlns=\"bar:module\"></foo-bar-container>"
            + "<foo-bar-container xmlns=\"foo:module\"></foo-bar-container>"
            + "</data>", output.toString(StandardCharsets.UTF_8));
    }
}