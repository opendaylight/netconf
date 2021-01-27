/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.test;

//import com.google.common.util.concurrent.ListenableFuture;
//import javax.inject.Inject;
//import javax.inject.Singleton;
//import org.apache.aries.blueprint.annotation.service.Reference;
//import org.eclipse.jdt.annotation.NonNull;
//import org.opendaylight.mdsal.binding.dom.codec.api.BindingNormalizedNodeSerializer;
//import org.opendaylight.mdsal.dom.api.DOMMountPoint;
//import org.opendaylight.mdsal.dom.api.DOMMountPointListener;
//import org.opendaylight.mdsal.dom.api.DOMMountPointService;
//import org.opendaylight.mdsal.dom.api.DOMRpcService;
//import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.CreateSubscriptionInput;
//import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.CreateSubscriptionInputBuilder;
//import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.CreateSubscriptionOutput;
//import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
//import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.StreamNameType;
//import org.opendaylight.yangtools.yang.binding.Action;
//import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
//import org.opendaylight.yangtools.yang.common.QName;
//import org.opendaylight.yangtools.yang.common.RpcResult;
//import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
//import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import static java.util.Objects.requireNonNull;

//@Singleton
public class FoobarCounter {

//    private static final Logger LOG = LoggerFactory.getLogger(FoobarCounter.class);
//    private static final String STREAM_NAME = "STREAM_NAME";
//    private final InstanceIdentifier<CreateSubscriptionInput> IID =
//        InstanceIdentifier.create(CreateSubscriptionInput.class);
//    private final DOMMountPointService domMountPointService;
//    private final BindingNormalizedNodeSerializer serializer;
//
//    @Inject
//    public FoobarCounter(final @Reference DOMMountPointService domMountPointService,
//                         @Reference BindingNormalizedNodeSerializer serializer) {
//        this.domMountPointService = requireNonNull(domMountPointService);
//        this.serializer = serializer;
//        domMountPointService.registerProvisionListener(new TestNodeMountPointListener());
//    }
//
//    private class TestNodeMountPointListener implements DOMMountPointListener {
//
//        @Override
//        public void onMountPointCreated(final YangInstanceIdentifier path) {
//            LOG.info("Mountpoint created: {}", path);
//            final String nodeId;
//            if (path.getLastPathArgument() instanceof YangInstanceIdentifier.NodeIdentifierWithPredicates) {
//                QName nodeQn = QName.create(Node.QNAME, "node-id").intern();
//                final Object obj = ((YangInstanceIdentifier.NodeIdentifierWithPredicates) path.getLastPathArgument()).getValue(nodeQn);
//                if (obj instanceof String) {
//                   nodeId = (String) obj;
//                }
//                else {
//                    return;
//                }
//            } else {
//                return;
//            }
//            final DOMMountPoint mountPoint = domMountPointService.getMountPoint(path).get();
//            final DOMRpcService rpcService = mountPoint.getService(DOMRpcService.class).get();
//            //final DOMSchemaService schemaService = mountPoint.getService(DOMSchemaService.class).get();
//            //final DOMActionService actionService = mountPoint.getService(DOMActionService.class).get();
//            final CreateSubscriptionInputBuilder subscriptionInputBuilder = new CreateSubscriptionInputBuilder();
//            subscriptionInputBuilder.setStream(new StreamNameType(STREAM_NAME));
//            final CreateSubscriptionInput input = subscriptionInputBuilder.build();
////            Map.Entry<YangInstanceIdentifier, NormalizedNode<?, ?>> entry = serializer.toNormalizedNode(IID, input);
////            serializer.toLazyNormalizedNodeActionInput(NotificationsService.class, input);
////            rpcService.invokeRpc(CreateSubscriptionInput.QNAME, entry.getValue());
////            EffectiveModelContext context = schemaService.getGlobalContext();
////            LOG.info("context {}", context.toString());
//
//            LOG.info("Triggering notification stream {} for node {}", STREAM_NAME, nodeId);
//            QName CREATE_SUBSCRIPTION = QName.create(CreateSubscriptionInput.QNAME, "create-subscription");
//            final ContainerNode nnInput = serializer.toNormalizedNodeRpcData(input);
//           // rpcService.invokeRpc(CREATE_SUBSCRIPTION, nnInput);
//
//        }
//
//        @Override
//        public void onMountPointRemoved(final YangInstanceIdentifier path) {
//            LOG.info("MountPointDestroyed: {}", path);
//        }
//    }
//
//    private class SubscriptionService implements Action<InstanceIdentifier<CreateSubscriptionInput>, CreateSubscriptionInput, CreateSubscriptionOutput> {
//        @Override
//        public @NonNull ListenableFuture<RpcResult<CreateSubscriptionOutput>> invoke(@NonNull InstanceIdentifier<CreateSubscriptionInput> path, @NonNull CreateSubscriptionInput input) {
//            return null;
//        }
//    }

}
