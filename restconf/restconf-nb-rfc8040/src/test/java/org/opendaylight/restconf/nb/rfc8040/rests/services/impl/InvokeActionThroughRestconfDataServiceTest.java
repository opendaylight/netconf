/*
 * Copyright (c) 2021 PANTHEON.tech s.r.o. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;
import static org.mockito.Mockito.mock;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Optional;
import javax.ws.rs.core.Response;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.dom.adapter.AdapterContext;
import org.opendaylight.mdsal.binding.dom.adapter.ConstantAdapterContext;
import org.opendaylight.mdsal.binding.dom.adapter.CurrentAdapterSerializer;
import org.opendaylight.mdsal.binding.dom.codec.impl.BindingCodecContext;
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMActionImplementation;
import org.opendaylight.mdsal.dom.api.DOMActionInstance;
import org.opendaylight.mdsal.dom.api.DOMActionProviderService;
import org.opendaylight.mdsal.dom.api.DOMActionResult;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.broker.DOMRpcRouter;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfStreamsSubscriptionService;
import org.opendaylight.restconf.nb.rfc8040.streams.Configuration;
import org.opendaylight.yang.gen.v1.instance.identifier.module.rev140117.Cont;
import org.opendaylight.yang.gen.v1.instance.identifier.module.rev140117.cont.Cont1;
import org.opendaylight.yang.gen.v1.instance.identifier.module.rev140117.cont.cont1.Reset;
import org.opendaylight.yang.gen.v1.instance.identifier.module.rev140117.cont.cont1.reset.Input;
import org.opendaylight.yang.gen.v1.instance.identifier.module.rev140117.cont.cont1.reset.Output;
import org.opendaylight.yang.gen.v1.instance.identifier.module.rev140117.cont.cont1.reset.OutputBuilder;
import org.opendaylight.yangtools.yang.binding.Action;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class InvokeActionThroughRestconfDataServiceTest {

    private static final String PATH_FOR_NEW_SCHEMA_CONTEXT = "/instanceidentifier/yang";

    private RestconfDataServiceImpl dataService;
    private DOMActionProviderService domActionProviderService;
    private EffectiveModelContext contextRef;

    @Before
    public void setUp() throws Exception {
        final DOMDataBroker mockDataBroker = mock(DOMDataBroker.class);

        this.contextRef = YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles(PATH_FOR_NEW_SCHEMA_CONTEXT));
        final SchemaContextHandler schemaContextHandler
                = new SchemaContextHandler(mockDataBroker, mock(DOMSchemaService.class));
        schemaContextHandler.onModelContextUpdated(this.contextRef);

        DOMRpcRouter domRpcRouter = new DOMRpcRouter();
        domRpcRouter.onModelContextUpdated(schemaContextHandler.get());
        this.domActionProviderService = domRpcRouter.getActionProviderService();

        this.dataService = new RestconfDataServiceImpl(schemaContextHandler, mockDataBroker,
                mock(DOMMountPointService.class), mock(RestconfStreamsSubscriptionService.class),
                domRpcRouter.getActionService(), mock(Configuration.class));
        // Register Reset Action
        final DOMDataTreeIdentifier domDataTreeIdentifier =
                new DOMDataTreeIdentifier(LogicalDatastoreType.OPERATIONAL, getResetActionYIID());
        final BindingCodecContext bindingCodecContext
                = new BindingCodecContext(BindingRuntimeHelpers.createRuntimeContext());
        final AdapterContext adapterContext = new ConstantAdapterContext(bindingCodecContext);
        final DOMActionImpl domAction = new DOMActionImpl(adapterContext, Reset.class, new ResetAction());

        domActionProviderService.registerActionImplementation(domAction,
                DOMActionInstance.of(SchemaNodeIdentifier.Absolute.of(Cont.QNAME, Cont1.QNAME, Reset.QNAME),
                        domDataTreeIdentifier));
    }

    @Test
    public void testInvokeAction() {
        final Optional<ActionDefinition> action = getResetActionDefinition();
        Assert.assertTrue(action.isPresent());
        final InstanceIdentifierContext<? extends SchemaNode> resetIIDContext
                = new InstanceIdentifierContext<SchemaNode>(getResetActionYIID(), action.get(), null, this.contextRef);
        final ContainerNode containerNode = getResetActionContainerNode();
        // Invoke Reset Action
        Response resetActionResponse
                = this.dataService.invokeAction(new NormalizedNodeContext(resetIIDContext, containerNode));
        // Test response from Reset Action
        Assert.assertNotNull(resetActionResponse.getEntity());
    }

    private ContainerNode getResetActionContainerNode() {
        final LeafNode<Object> delay = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(Input.QNAME, "delay")))
                .withValue(Uint32.valueOf(10))
                .build();
        return Builders.containerBuilder()
                .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(QName.create(Reset.QNAME, "input")))
                .withChild(delay)
                .build();
    }

    private Optional<ActionDefinition> getResetActionDefinition() {
        YangInstanceIdentifier iidBase = YangInstanceIdentifier.builder()
                .node(Cont.QNAME)
                .node(Cont1.QNAME)
                .build();
        DataSchemaNode dataSchemaNode = DataSchemaContextTree.from(this.contextRef)
                .findChild(iidBase)
                .orElseThrow()
                .getDataSchemaNode();
        ActionNodeContainer dataSchemaNode1 = (ActionNodeContainer) dataSchemaNode;
        return dataSchemaNode1.findAction(Reset.QNAME);
    }

    private YangInstanceIdentifier getResetActionYIID() {
        final YangInstanceIdentifier yangInstanceIdentifier = YangInstanceIdentifier
                .of(Cont.QNAME)
                .node(Cont1.QNAME)
                .node(Reset.QNAME);
        return yangInstanceIdentifier;
    }

    private static final class DOMActionImpl implements DOMActionImplementation {

        private final Class<? extends Action<?, ?, ?>> actionInterface;
        private final AdapterContext adapterContext;
        private final Action implementation;

        DOMActionImpl(final AdapterContext adapterContext,
                      final Class<? extends Action<?, ?, ?>> actionInterface, final Action<?, ?, ?> implementation) {
            this.adapterContext = requireNonNull(adapterContext);
            this.actionInterface = requireNonNull(actionInterface);
            this.implementation = requireNonNull(implementation);
        }

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public ListenableFuture<? extends DOMActionResult> invokeAction(final SchemaNodeIdentifier.Absolute type,
                                                                        final DOMDataTreeIdentifier path,
                                                                        final ContainerNode input) {
            final CurrentAdapterSerializer codec = adapterContext.currentSerializer();
            return implementation.invoke(
                    verifyNotNull(codec.fromYangInstanceIdentifier(path.getRootIdentifier())),
                    codec.fromNormalizedNodeActionInput(actionInterface, input));
        }
    }

    private static final class ResetAction implements Reset {

        @Override
        public @NonNull ListenableFuture<@NonNull RpcResult<@NonNull Output>> invoke(
                @NonNull InstanceIdentifier<Cont1> path, @NonNull Input input) {
            return Futures.immediateFuture(RpcResultBuilder.success(new OutputBuilder().build()).build());
        }
    }
}
