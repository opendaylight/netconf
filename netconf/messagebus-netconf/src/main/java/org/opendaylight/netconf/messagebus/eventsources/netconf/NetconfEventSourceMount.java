/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.messagebus.eventsources.netconf;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.CheckedFuture;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javassist.ClassPool;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationListener;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.md.sal.dom.api.DOMService;
import org.opendaylight.mdsal.binding.dom.codec.gen.impl.StreamWriterGenerator;
import org.opendaylight.mdsal.binding.dom.codec.impl.BindingNormalizedNodeCodecRegistry;
import org.opendaylight.mdsal.binding.generator.impl.ModuleInfoBackedContext;
import org.opendaylight.mdsal.binding.generator.util.BindingRuntimeContext;
import org.opendaylight.mdsal.binding.generator.util.JavassistUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.CreateSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.CreateSubscriptionInputBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.Netconf;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.Streams;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

/**
 * Facade of mounted netconf device.
 */
class NetconfEventSourceMount {

    private static final BindingNormalizedNodeCodecRegistry CODEC_REGISTRY;
    private static final YangInstanceIdentifier STREAMS_PATH = YangInstanceIdentifier.builder().node(Netconf.QNAME)
            .node(Streams.QNAME).build();
    private static final SchemaPath CREATE_SUBSCRIPTION = SchemaPath
            .create(true, QName.create(CreateSubscriptionInput.QNAME, "create-subscription"));

    static {
        final ModuleInfoBackedContext moduleInfoBackedContext = ModuleInfoBackedContext.create();
        moduleInfoBackedContext.addModuleInfos(Collections.singletonList(org.opendaylight.yang.gen.v1.urn.ietf.params
                .xml.ns.netmod.notification.rev080714.$YangModuleInfoImpl.getInstance()));
        final Optional<SchemaContext> schemaContextOptional = moduleInfoBackedContext.tryToCreateSchemaContext();
        Preconditions.checkState(schemaContextOptional.isPresent());
        SchemaContext notificationsSchemaCtx = schemaContextOptional.get();

        final JavassistUtils javassist = JavassistUtils.forClassPool(ClassPool.getDefault());
        CODEC_REGISTRY = new BindingNormalizedNodeCodecRegistry(StreamWriterGenerator.create(javassist));
        CODEC_REGISTRY.onBindingRuntimeContextUpdated(BindingRuntimeContext.create(moduleInfoBackedContext,
                notificationsSchemaCtx));
    }

    private final DOMMountPoint mountPoint;
    private final DOMRpcService rpcService;
    private final DOMNotificationService notificationService;
    private final DOMDataBroker dataBroker;
    private final Node node;
    private final String nodeId;

    NetconfEventSourceMount(final Node node, final DOMMountPoint mountPoint) {
        this.mountPoint = mountPoint;
        this.node = node;
        this.nodeId = node.getNodeId().getValue();
        this.rpcService = getService(mountPoint, DOMRpcService.class);
        this.notificationService = getService(mountPoint, DOMNotificationService.class);
        this.dataBroker = getService(mountPoint, DOMDataBroker.class);
    }

    private static <T extends DOMService> T getService(DOMMountPoint mountPoint, Class<T> service) {
        final Optional<T> optional = mountPoint.getService(service);
        Preconditions.checkState(optional.isPresent(), "Service not present on mount point: %s", service.getName());
        return optional.get();
    }

    Node getNode() {
        return node;
    }

    String getNodeId() {
        return nodeId;
    }

    /**
     * Invokes create-subscription rpc on mounted device stream. If lastEventTime is provided and stream supports
     * replay,
     * rpc will be invoked with start time parameter.
     *
     * @param stream        stream
     * @param lastEventTime last event time
     * @return rpc result
     */
    CheckedFuture<DOMRpcResult, DOMRpcException> invokeCreateSubscription(final Stream stream,
                                                                          final Optional<Date> lastEventTime) {
        final CreateSubscriptionInputBuilder inputBuilder = new CreateSubscriptionInputBuilder()
                .setStream(stream.getName());
        if (lastEventTime.isPresent() && stream.isReplaySupport()) {
            final ZonedDateTime dateTime = lastEventTime.get().toInstant().atZone(ZoneId.systemDefault());
            final String formattedDate = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(dateTime);
            inputBuilder.setStartTime(new DateAndTime(formattedDate));
        }
        final CreateSubscriptionInput input = inputBuilder.build();
        final ContainerNode nnInput = CODEC_REGISTRY.toNormalizedNodeRpcData(input);
        return rpcService.invokeRpc(CREATE_SUBSCRIPTION, nnInput);
    }

    /**
     * Invokes create-subscription rpc on mounted device stream.
     *
     * @param stream stream
     * @return rpc result
     */
    CheckedFuture<DOMRpcResult, DOMRpcException> invokeCreateSubscription(final Stream stream) {
        return invokeCreateSubscription(stream, Optional.absent());
    }

    /**
     * Returns list of streams avaliable on device.
     *
     * @return list of streams
     * @throws ReadFailedException if data read fails
     */
    List<Stream> getAvailableStreams() throws ReadFailedException {
        DOMDataReadOnlyTransaction tx = dataBroker.newReadOnlyTransaction();
        CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> checkFeature = tx
                .read(LogicalDatastoreType.OPERATIONAL, STREAMS_PATH);
        Optional<NormalizedNode<?, ?>> streams = checkFeature.checkedGet();
        if (streams.isPresent()) {
            Streams streams1 = (Streams) CODEC_REGISTRY.fromNormalizedNode(STREAMS_PATH, streams.get()).getValue();
            return streams1.getStream();
        }
        return Collections.emptyList();
    }

    SchemaContext getSchemaContext() {
        return mountPoint.getSchemaContext();
    }

    /**
     * Registers notification listener to receive a set of notifications.
     *
     * @param listener         listener
     * @param notificationPath notification path
     * @return ListenerRegistration
     * @see DOMNotificationService#registerNotificationListener(DOMNotificationListener, SchemaPath...)
     */
    ListenerRegistration<DOMNotificationListener> registerNotificationListener(DOMNotificationListener listener,
                                                                               SchemaPath notificationPath) {
        return notificationService.registerNotificationListener(listener, notificationPath);
    }

}
