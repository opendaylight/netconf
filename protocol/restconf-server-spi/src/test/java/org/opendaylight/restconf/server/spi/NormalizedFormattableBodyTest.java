/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.common.DatabindPath.Data;
import org.opendaylight.restconf.server.api.testlib.AbstractInstanceIdentifierTest;
import org.opendaylight.restconf.server.api.testlib.AbstractJukeboxTest;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

class NormalizedFormattableBodyTest extends AbstractInstanceIdentifierTest {
    @Test
    void testWriteEmptyRootContainer() throws Exception {
        final var body = NormalizedFormattableBody.of(new Data(IID_DATABIND), ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(SchemaContext.NAME))
            .build(), NormalizedNodeWriterFactory.of());

        // FIXME: NETCONF-855: this is wrong, the namespace should be 'urn:ietf:params:xml:ns:yang:ietf-restconf'
        AbstractJukeboxTest.assertFormat("<data xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"></data>",
            body::formatToXML, false);
        AbstractJukeboxTest.assertFormat("{}", body::formatToJSON, false);
    }

    @Test
    void testRootContainerWrite() throws Exception {
        final var body = NormalizedFormattableBody.of(new Data(IID_DATABIND), ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(SchemaContext.NAME))
            .withChild(ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(new NodeIdentifier(
                    QName.create("foo:module", "2016-09-29", "foo-bar-container")))
                .build())
            .withChild(ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(new NodeIdentifier(
                    QName.create("bar:module", "2016-09-29", "foo-bar-container")))
                .build())
            .build(), NormalizedNodeWriterFactory.of());

        // FIXME: NETCONF-855: this is wrong, the namespace should be 'urn:ietf:params:xml:ns:yang:ietf-restconf'
        AbstractJukeboxTest.assertFormat("""
            <data xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">\
            <foo-bar-container xmlns="bar:module"></foo-bar-container>\
            <foo-bar-container xmlns="foo:module"></foo-bar-container>\
            </data>""", body::formatToXML, false);
        AbstractJukeboxTest.assertFormat("""
            {"bar-module:foo-bar-container":{}}""", body::formatToJSON, false);
    }
}