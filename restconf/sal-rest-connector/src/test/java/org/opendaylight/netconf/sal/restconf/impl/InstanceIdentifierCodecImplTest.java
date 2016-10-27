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
import static org.opendaylight.netconf.sal.restconf.impl.RestCodec.InstanceIdentifierCodecImpl;

import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.yangtools.concepts.Codec;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class InstanceIdentifierCodecImplTest {

    private Codec<IdentityValuesDTO, YangInstanceIdentifier> instanceIdentifierDTO;
    private YangInstanceIdentifier instanceIdentifierBadNamespace;
    private YangInstanceIdentifier instanceIdentifierOKList;
    private YangInstanceIdentifier instanceIdentifierOKLeafList;
    private SchemaContext schemaContext;

    @Before
    public void setUp() throws Exception {
        schemaContext = TestRestconfUtils.loadSchemaContext("/restconf/parser/deserializer");
        instanceIdentifierDTO = new InstanceIdentifierCodecImpl(null);
        ControllerContext.getInstance().setGlobalSchema(schemaContext);

        final QName baseQName = QName.create("deserializer:test", "2016-06-06", "deserializer-test");
        final QName contA = QName.create(baseQName, "contA");
        final QName leafList = QName.create(baseQName, "leaf-list-A");

        instanceIdentifierOKLeafList = YangInstanceIdentifier.builder()
                .node(contA)
                .node(new YangInstanceIdentifier.NodeWithValue<>(leafList, "instance"))
                .build();

        instanceIdentifierOKList = YangInstanceIdentifier.builder()
                .node(new YangInstanceIdentifier.NodeIdentifierWithPredicates(
                        QName.create(baseQName, "list-one-key"),
                        QName.create(QName.create(baseQName, "list-one-key"), "name"), "value"))
                .build();

        instanceIdentifierBadNamespace = YangInstanceIdentifier.builder()
                .nodeWithKey(QName.create("nonexistent:module", "2016-10-17", "nonexistent-1"),
                        QName.create("nonexistent:module", "2016-10-17", "nonexistent"),
                        "value")
                .build();
    }

    @Test
    public void testSerializeDeserializeList() throws Exception {
        final IdentityValuesDTO valuesDTO =
                instanceIdentifierDTO.serialize(instanceIdentifierOKList);

        final YangInstanceIdentifier deserializedIdentifier =
                instanceIdentifierDTO.deserialize(valuesDTO);
        assertEquals(instanceIdentifierOKList, deserializedIdentifier);
    }

    @Test
    public void testSerializeDeserializeLeafList() throws Exception {
        final IdentityValuesDTO valuesDTO =
                instanceIdentifierDTO.serialize(instanceIdentifierOKLeafList);

        final YangInstanceIdentifier deserializedIdentifier =
                instanceIdentifierDTO.deserialize(valuesDTO);
        assertEquals(instanceIdentifierOKLeafList, deserializedIdentifier);
    }

    @Test
    public void testSerializeDeserializeBadModuleNamespace() throws Exception {
        final IdentityValuesDTO valuesDTO =
                instanceIdentifierDTO.serialize(instanceIdentifierBadNamespace);
        assertEquals("nonexistent-1", valuesDTO.getValuesWithNamespaces().get(0).getValue());
        assertEquals("nonexistent:module", valuesDTO.getValuesWithNamespaces().get(0).getNamespace());

        final YangInstanceIdentifier deserializedIdentifier =
                instanceIdentifierDTO.deserialize(valuesDTO);
        assertNull(deserializedIdentifier);
    }
}