/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.common.mdsal;

import static org.junit.Assert.assertFalse;

import java.util.List;
import javax.xml.transform.dom.DOMResult;
import org.junit.Test;
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.$YangModuleInfoImpl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Sessions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.ElementSelectors;

public class NormalizedDataUtilTest {
    @Test
    public void testWriteNormalizedNode() throws Exception {
        final var context = BindingRuntimeHelpers.createEffectiveModel(List.of($YangModuleInfoImpl.getInstance()));
        final var result = new DOMResult(XmlUtil.newDocument());
        NormalizedDataUtil.writeNormalizedNode(Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(Sessions.QNAME))
            .withChild(Builders.mapBuilder()
                .withNodeIdentifier(new NodeIdentifier(Session.QNAME))
                .withChild(Builders.mapEntryBuilder()
                    .withNodeIdentifier(
                        NodeIdentifierWithPredicates.of(Session.QNAME, QName.create(Session.QNAME, "session-id"), 1L))
                    .withChild(ImmutableNodes.leafNode(QName.create(Session.QNAME, "username"), "admin"))
                    .build())
                .build())
            .build(), result, context, Absolute.of(NetconfState.QNAME));

        final var diff = DiffBuilder.compare(result.getNode())
            .withTest("""
                <sessions xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring">
                    <session>
                        <session-id>1</session-id>
                        <username>admin</username>
                    </session>
                </sessions>
                """)
            .withNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byNameAndText))
            .ignoreWhitespace()
            .checkForSimilar()
            .build();

        assertFalse(diff.toString(), diff.hasDifferences());
    }
}
