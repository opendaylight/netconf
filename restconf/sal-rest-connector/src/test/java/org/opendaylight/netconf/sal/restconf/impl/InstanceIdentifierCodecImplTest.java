package org.opendaylight.netconf.sal.restconf.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.MockitoAnnotations.initMocks;

import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.yangtools.concepts.Codec;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class InstanceIdentifierCodecImplTest {

    private Codec instanceIdentifierDTO;
    private YangInstanceIdentifier instanceIdentifierBadNamespace;
    private YangInstanceIdentifier instanceIdentifierOKList;
    private YangInstanceIdentifier instanceIdentifierOKLeafList;
    private QName baseQname;
    private QName contA;
    private QName leafList;
    private SchemaContext schemaContext;
    //Fields use for simulate DOMMountPoint
    //  private static Field mountPoint;

    @Before
    public void setUp() throws Exception {

        initMocks(this);
        //    InstanceIdentifierCodecImplTest.mountPoint = RestCodec.InstanceIdentifierCodecImpl.class.getDeclaredField("mountPoint");
        //    InstanceIdentifierCodecImplTest.mountPoint.set(RestCodec.InstanceIdentifierCodecImpl.class, mock(DOMMountPoint.class));

        schemaContext = TestRestconfUtils.loadSchemaContext("/restconf/parser/deserializer");
        instanceIdentifierDTO = new RestCodec.InstanceIdentifierCodecImpl(null);

        baseQname = QName.create("deserializer:test", "2016-06-06", "deserializer-test");
        contA = QName.create(baseQname, "contA");
        leafList = QName.create(baseQname, "leaf-list-A");

        instanceIdentifierOKLeafList = YangInstanceIdentifier.builder()
                .node(contA)
                .node(new YangInstanceIdentifier.NodeWithValue(leafList, "instance"))
                .build();

        instanceIdentifierOKList = YangInstanceIdentifier.builder()
                .node(new YangInstanceIdentifier.NodeIdentifierWithPredicates(
                        QName.create(baseQname, "list-one-key"),
                        QName.create(QName.create(baseQname, "list-one-key"), "name"), "value"))
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
                (IdentityValuesDTO) instanceIdentifierDTO.serialize(instanceIdentifierOKList);

        ControllerContext.getInstance().setGlobalSchema(schemaContext);
        final YangInstanceIdentifier deserializedIdentifier =
                (YangInstanceIdentifier) instanceIdentifierDTO.deserialize(valuesDTO);
        assertEquals(instanceIdentifierOKList, deserializedIdentifier);
    }

    @Test
    public void testSerializeDeserializeLeafList() throws Exception {
        final IdentityValuesDTO valuesDTO =
                (IdentityValuesDTO) instanceIdentifierDTO.serialize(instanceIdentifierOKLeafList);

        ControllerContext.getInstance().setGlobalSchema(schemaContext);
        final YangInstanceIdentifier deserializedIdentifier =
                (YangInstanceIdentifier) instanceIdentifierDTO.deserialize(valuesDTO);
        assertEquals(instanceIdentifierOKLeafList, deserializedIdentifier);
    }

    @Test
    public void testSerializeDeserializeBadModuleNamespace() throws Exception {
        final IdentityValuesDTO valuesDTO =
                (IdentityValuesDTO) instanceIdentifierDTO.serialize(instanceIdentifierBadNamespace);
        assertEquals("nonexistent-1", valuesDTO.getValuesWithNamespaces().get(0).getValue());
        assertEquals("nonexistent:module", valuesDTO.getValuesWithNamespaces().get(0).getNamespace());

        ControllerContext.getInstance().setGlobalSchema(schemaContext);
        final YangInstanceIdentifier deserializedIdentifier =
                (YangInstanceIdentifier) instanceIdentifierDTO.deserialize(valuesDTO);
        assertNull(deserializedIdentifier);
    }
}