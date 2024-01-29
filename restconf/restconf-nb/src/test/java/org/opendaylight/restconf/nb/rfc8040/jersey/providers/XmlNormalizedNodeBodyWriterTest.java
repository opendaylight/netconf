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
import org.opendaylight.restconf.nb.rfc8040.AbstractInstanceIdentifierTest;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class XmlNormalizedNodeBodyWriterTest extends AbstractInstanceIdentifierTest {
    @Test
    public void testWriteEmptyRootContainer() throws IOException {
        final EffectiveModelContext schemaContext = mock(EffectiveModelContext.class);

        final NormalizedNodePayload nodePayload = new NormalizedNodePayload(Inference.ofDataTreePath(schemaContext),
            ImmutableNodes.newContainerBuilder().withNodeIdentifier(new NodeIdentifier(SchemaContext.NAME)).build());

        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final XmlNormalizedNodeBodyWriter xmlWriter = new XmlNormalizedNodeBodyWriter();
        xmlWriter.writeTo(nodePayload, null, null, null, MediaType.APPLICATION_XML_TYPE, null, output);

        // FIXME: NETCONF-855: this is wrong, the namespace should be 'urn:ietf:params:xml:ns:yang:ietf-restconf'
        assertEquals("<data xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"></data>",
            output.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void testRootContainerWrite() throws IOException {
        final NormalizedNodePayload nodePayload = new NormalizedNodePayload(
            Inference.ofDataTreePath(IID_SCHEMA),
            ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(new NodeIdentifier(SchemaContext.NAME))
                .withChild(ImmutableNodes.newContainerBuilder()
                    .withNodeIdentifier(new NodeIdentifier(
                        QName.create("foo:module", "2016-09-29", "foo-bar-container")))
                    .build())
                .withChild(ImmutableNodes.newContainerBuilder()
                    .withNodeIdentifier(new NodeIdentifier(
                        QName.create("bar:module", "2016-09-29", "foo-bar-container")))
                    .build())
                .build());

        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final XmlNormalizedNodeBodyWriter xmlWriter = new XmlNormalizedNodeBodyWriter();
        xmlWriter.writeTo(nodePayload, null, null, null, MediaType.APPLICATION_XML_TYPE, null, output);

        assertEquals("""
            <data xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">\
            <foo-bar-container xmlns="bar:module"></foo-bar-container>\
            <foo-bar-container xmlns="foo:module"></foo-bar-container>\
            </data>""", output.toString(StandardCharsets.UTF_8));
    }
}