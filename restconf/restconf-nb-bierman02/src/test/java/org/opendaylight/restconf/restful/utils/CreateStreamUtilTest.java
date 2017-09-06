/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.restful.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeAttrBuilder;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.util.SchemaNodeUtils;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class CreateStreamUtilTest {

    private static final String PATH_FOR_NEW_SCHEMA_CONTEXT = "/streams";

    private NormalizedNodeContext payload;
    private SchemaContextRef refSchemaCtx;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        this.refSchemaCtx = new SchemaContextRef(
                YangParserTestUtils.parseYangSources(TestRestconfUtils.loadFiles(PATH_FOR_NEW_SCHEMA_CONTEXT)));
    }

    @Test
    public void createStreamTest() {
        this.payload = prepareDomPayload("create-data-change-event-subscription", "input", "toaster", "path");
        final DOMRpcResult result = CreateStreamUtil.createDataChangeNotifiStream(this.payload, this.refSchemaCtx);
        assertEquals(result.getErrors(), Collections.emptyList());
        final NormalizedNode<?, ?> testedNn = result.getResult();
        assertNotNull(testedNn);
        final NormalizedNodeContext contextRef = prepareDomPayload("create-data-change-event-subscription", "output",
                "data-change-event-subscription/toaster:toaster/datastore=CONFIGURATION/scope=BASE", "stream-name");
        assertEquals(contextRef.getData(), testedNn);
    }

    @Test(expected = RestconfDocumentedException.class)
    public void createStreamWrongValueTest() {
        this.payload = prepareDomPayload("create-data-change-event-subscription", "input", "String value", "path");
        final DOMRpcResult result = CreateStreamUtil.createDataChangeNotifiStream(this.payload, this.refSchemaCtx);
        assertEquals(result.getErrors(), Collections.emptyList());
    }

    @Test(expected = RestconfDocumentedException.class)
    public void createStreamWrongInputRpcTest() {
        this.payload = prepareDomPayload("create-data-change-event-subscription2", "input", "toaster", "path2");
        final DOMRpcResult result = CreateStreamUtil.createDataChangeNotifiStream(this.payload, this.refSchemaCtx);
        assertEquals(result.getErrors(), Collections.emptyList());
    }

    private NormalizedNodeContext prepareDomPayload(final String rpcName, final String inputOutput,
            final String toasterValue, final String inputOutputName) {
        final SchemaContext schema = this.refSchemaCtx.get();
        final Module rpcModule = schema.findModuleByName("sal-remote", null);
        assertNotNull(rpcModule);
        final QName rpcQName = QName.create(rpcModule.getQNameModule(), rpcName);
        final QName rpcInputQName = QName.create(rpcModule.getQNameModule(), inputOutput);
        final Set<RpcDefinition> setRpcs = rpcModule.getRpcs();
        ContainerSchemaNode rpcInputSchemaNode = null;
        for (final RpcDefinition rpc : setRpcs) {
            if (rpcQName.isEqualWithoutRevision(rpc.getQName())) {
                rpcInputSchemaNode = SchemaNodeUtils.getRpcDataSchema(rpc, rpcInputQName);
                break;
            }
        }
        assertNotNull(rpcInputSchemaNode);

        final DataContainerNodeAttrBuilder<YangInstanceIdentifier.NodeIdentifier, ContainerNode> container =
                Builders.containerBuilder(rpcInputSchemaNode);

        final QName lfQName = QName.create(rpcModule.getQNameModule(), inputOutputName);
        final DataSchemaNode lfSchemaNode = rpcInputSchemaNode.getDataChildByName(lfQName);

        assertTrue(lfSchemaNode instanceof LeafSchemaNode);

        final Object o;
        if ("toaster".equals(toasterValue)) {
            final QName rpcQname = QName.create("http://netconfcentral.org/ns/toaster", "2009-11-20", toasterValue);
            o = YangInstanceIdentifier.builder().node(rpcQname).build();
        } else {
            o = toasterValue;
        }
        final LeafNode<Object> lfNode = (Builders.leafBuilder((LeafSchemaNode) lfSchemaNode)
                .withValue(o)).build();
        container.withChild(lfNode);

        return new NormalizedNodeContext(new InstanceIdentifierContext<>(null, rpcInputSchemaNode, null, schema),
                container.build());
    }
}
