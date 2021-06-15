/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.builder.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.SchemaAwareBuilders;
import org.opendaylight.yangtools.yang.model.api.ContainerLike;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class CreateStreamUtilTest {
    private static final String PATH_FOR_NEW_SCHEMA_CONTEXT = "/streams";

    private NormalizedNodeContext payload;
    private EffectiveModelContext refSchemaCtx;
    private ParserIdentifier parserIdentifier;

    @Before
    public void setUp() throws Exception {
        this.refSchemaCtx =
                YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles(PATH_FOR_NEW_SCHEMA_CONTEXT));

        final SchemaContextHandler schemaContextHandler = mock(SchemaContextHandler.class);
        when(schemaContextHandler.get()).thenReturn(refSchemaCtx);
        parserIdentifier = new ParserIdentifier(mock(DOMMountPointService.class),
                schemaContextHandler, mock(DOMYangTextSourceProvider.class));
    }

    @Test
    public void createStreamTest() {
        this.payload = prepareDomPayload("create-data-change-event-subscription", RpcDefinition::getInput, "toaster",
            "path");
        final DOMRpcResult result = CreateStreamUtil.createDataChangeNotifiStream(this.payload, this.parserIdentifier);
        assertEquals(result.getErrors(), Collections.emptyList());
        final NormalizedNode testedNn = result.getResult();
        assertNotNull(testedNn);
        final NormalizedNodeContext contextRef = prepareDomPayload("create-data-change-event-subscription",
            RpcDefinition::getOutput,
            "data-change-event-subscription/toaster:toaster/datastore=CONFIGURATION/scope=BASE", "stream-name");
        assertEquals(contextRef.getData(), testedNn);
    }

    @Test(expected = RestconfDocumentedException.class)
    public void createStreamWrongValueTest() {
        this.payload = prepareDomPayload("create-data-change-event-subscription", RpcDefinition::getInput,
            "String value", "path");
        final DOMRpcResult result = CreateStreamUtil.createDataChangeNotifiStream(this.payload, this.parserIdentifier);
        assertEquals(result.getErrors(), Collections.emptyList());
    }

    @Test(expected = RestconfDocumentedException.class)
    public void createStreamWrongInputRpcTest() {
        this.payload = prepareDomPayload("create-data-change-event-subscription2", RpcDefinition::getInput, "toaster",
            "path2");
        final DOMRpcResult result = CreateStreamUtil.createDataChangeNotifiStream(this.payload, this.parserIdentifier);
        assertEquals(result.getErrors(), Collections.emptyList());
    }

    private NormalizedNodeContext prepareDomPayload(final String rpcName,
            final Function<RpcDefinition, ContainerLike> rpcToContainer, final String toasterValue,
            final String inputOutputName) {
        final EffectiveModelContext schema = this.refSchemaCtx;
        final Module rpcModule = schema.findModules("sal-remote").iterator().next();
        final QName rpcQName = QName.create(rpcModule.getQNameModule(), rpcName);
        ContainerLike rpcInputSchemaNode = null;
        for (final RpcDefinition rpc : rpcModule.getRpcs()) {
            if (rpcQName.isEqualWithoutRevision(rpc.getQName())) {
                rpcInputSchemaNode = rpcToContainer.apply(rpc);
                break;
            }
        }
        assertNotNull(rpcInputSchemaNode);

        final DataContainerNodeBuilder<YangInstanceIdentifier.NodeIdentifier, ContainerNode> container =
            SchemaAwareBuilders.containerBuilder(rpcInputSchemaNode);

        final QName lfQName = QName.create(rpcModule.getQNameModule(), inputOutputName);
        final DataSchemaNode lfSchemaNode = rpcInputSchemaNode.findDataChildByName(lfQName).orElseThrow();

        assertTrue(lfSchemaNode instanceof LeafSchemaNode);

        final Object o;
        if ("toaster".equals(toasterValue)) {
            final QName rpcQname = QName.create("http://netconfcentral.org/ns/toaster", "2009-11-20", toasterValue);
            o = YangInstanceIdentifier.builder().node(rpcQname).build();
        } else {
            o = toasterValue;
        }
        final LeafNode<Object> lfNode = SchemaAwareBuilders.leafBuilder((LeafSchemaNode) lfSchemaNode)
                .withValue(o).build();
        container.withChild(lfNode);

        return new NormalizedNodeContext(new InstanceIdentifierContext<>(null, rpcInputSchemaNode, null, schema),
                container.build());
    }
}
