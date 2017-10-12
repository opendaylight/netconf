/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.restconf.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.RestCodec.InstanceIdentifierCodecImpl;
import org.opendaylight.restconf.common.util.IdentityValuesDTO;
import org.opendaylight.yangtools.concepts.Codec;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class InstanceIdentifierCodecImplTest {

    private Codec<IdentityValuesDTO, YangInstanceIdentifier> instanceIdentifierDTO;
    private YangInstanceIdentifier instanceIdentifierBadNamespace;
    private YangInstanceIdentifier instanceIdentifierOKList;
    private YangInstanceIdentifier instanceIdentifierOKLeafList;
    private SchemaContext schemaContext;

    @Before
    public void setUp() throws Exception {
        this.schemaContext =
                YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles("/restconf/parser/deserializer"));
        this.instanceIdentifierDTO = new InstanceIdentifierCodecImpl(null);
        ControllerContext.getInstance().setGlobalSchema(this.schemaContext);

        final QName baseQName = QName.create("deserializer:test", "2016-06-06", "deserializer-test");
        final QName contA = QName.create(baseQName, "contA");
        final QName leafList = QName.create(baseQName, "leaf-list-A");

        this.instanceIdentifierOKLeafList = YangInstanceIdentifier.builder()
                .node(contA)
                .node(new YangInstanceIdentifier.NodeWithValue<>(leafList, "instance"))
                .build();

        this.instanceIdentifierOKList = YangInstanceIdentifier.builder()
                .node(new YangInstanceIdentifier.NodeIdentifierWithPredicates(
                        QName.create(baseQName, "list-one-key"),
                        QName.create(QName.create(baseQName, "list-one-key"), "name"), "value"))
                .build();

        this.instanceIdentifierBadNamespace = YangInstanceIdentifier.builder()
                .nodeWithKey(QName.create("nonexistent:module", "2016-10-17", "nonexistent-1"),
                        QName.create("nonexistent:module", "2016-10-17", "nonexistent"),
                        "value")
                .build();
    }

    @Test
    public void testSerializeDeserializeList() throws Exception {
        final IdentityValuesDTO valuesDTO =
                this.instanceIdentifierDTO.serialize(this.instanceIdentifierOKList);

        final YangInstanceIdentifier deserializedIdentifier =
                this.instanceIdentifierDTO.deserialize(valuesDTO);
        assertEquals(this.instanceIdentifierOKList, deserializedIdentifier);
    }

    @Test
    public void testSerializeDeserializeLeafList() throws Exception {
        final IdentityValuesDTO valuesDTO =
                this.instanceIdentifierDTO.serialize(this.instanceIdentifierOKLeafList);

        final YangInstanceIdentifier deserializedIdentifier =
                this.instanceIdentifierDTO.deserialize(valuesDTO);
        assertEquals(this.instanceIdentifierOKLeafList, deserializedIdentifier);
    }

    @Test
    public void testSerializeDeserializeBadModuleNamespace() throws Exception {
        final IdentityValuesDTO valuesDTO =
                this.instanceIdentifierDTO.serialize(this.instanceIdentifierBadNamespace);
        assertEquals("nonexistent-1", valuesDTO.getValuesWithNamespaces().get(0).getValue());
        assertEquals("nonexistent:module", valuesDTO.getValuesWithNamespaces().get(0).getNamespace());

        final YangInstanceIdentifier deserializedIdentifier =
                this.instanceIdentifierDTO.deserialize(valuesDTO);
        assertNull(deserializedIdentifier);
    }
}