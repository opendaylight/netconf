/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import java.util.List;
import java.util.function.Function;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.ContainerLike;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class CreateStreamUtilTest {
    private static EffectiveModelContext SCHEMA_CTX;

    @BeforeClass
    public static void setUp() {
        SCHEMA_CTX = YangParserTestUtils.parseYangResourceDirectory("/streams");
    }

    @Test
    public void createStreamTest() {
        final DOMRpcResult result = CreateStreamUtil.createDataChangeNotifiStream(
            prepareDomPayload("create-data-change-event-subscription", RpcDefinition::getInput, "toaster", "path"),
            SCHEMA_CTX);
        assertEquals(List.of(), result.errors());
        assertEquals(prepareDomPayload("create-data-change-event-subscription",
            RpcDefinition::getOutput,
            "data-change-event-subscription/toaster:toaster/datastore=CONFIGURATION/scope=BASE", "stream-name"),
            result.value());
    }

    @Test
    public void createStreamWrongValueTest() {
        final var payload = prepareDomPayload("create-data-change-event-subscription", RpcDefinition::getInput,
            "String value", "path");
        final var errors = assertThrows(RestconfDocumentedException.class,
            () -> CreateStreamUtil.createDataChangeNotifiStream(payload, SCHEMA_CTX)).getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals(ErrorType.APPLICATION, error.getErrorType());
        assertEquals(ErrorTag.OPERATION_FAILED, error.getErrorTag());
        assertEquals("Instance identifier was not normalized correctly", error.getErrorMessage());
    }

    @Test
    public void createStreamWrongInputRpcTest() {
        final var payload = prepareDomPayload("create-data-change-event-subscription2", RpcDefinition::getInput,
            "toaster", "path2");
        final var errors = assertThrows(RestconfDocumentedException.class,
            () -> CreateStreamUtil.createDataChangeNotifiStream(payload, SCHEMA_CTX)).getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals(ErrorType.APPLICATION, error.getErrorType());
        assertEquals(ErrorTag.OPERATION_FAILED, error.getErrorTag());
        assertEquals("Instance identifier was not normalized correctly", error.getErrorMessage());
    }

    private static ContainerNode prepareDomPayload(final String rpcName,
            final Function<RpcDefinition, ContainerLike> rpcToContainer, final String toasterValue,
            final String inputOutputName) {
        final Module rpcModule = SCHEMA_CTX.findModules("sal-remote").iterator().next();
        final QName rpcQName = QName.create(rpcModule.getQNameModule(), rpcName);

        ContainerLike containerSchemaNode = null;
        for (final RpcDefinition rpc : rpcModule.getRpcs()) {
            if (rpcQName.isEqualWithoutRevision(rpc.getQName())) {
                containerSchemaNode = rpcToContainer.apply(rpc);
                break;
            }
        }
        assertNotNull(containerSchemaNode);

        final QName lfQName = QName.create(rpcModule.getQNameModule(), inputOutputName);
        final DataSchemaNode lfSchemaNode = containerSchemaNode.getDataChildByName(lfQName);
        assertThat(lfSchemaNode, instanceOf(LeafSchemaNode.class));

        final Object o;
        if ("toaster".equals(toasterValue)) {
            final QName rpcQname = QName.create("http://netconfcentral.org/ns/toaster", "2009-11-20", toasterValue);
            o = YangInstanceIdentifier.of(rpcQname);
        } else {
            o = toasterValue;
        }

        return Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(containerSchemaNode.getQName()))
            .withChild(ImmutableNodes.leafNode(lfQName, o))
            .build();
    }
}
