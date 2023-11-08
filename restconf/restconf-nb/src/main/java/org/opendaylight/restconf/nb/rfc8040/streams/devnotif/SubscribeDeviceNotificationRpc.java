/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.devnotif;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import java.security.Principal;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.nb.rfc8040.streams.DeviceNotificationStream;
import org.opendaylight.restconf.nb.rfc8040.streams.ListenersBroker;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.restconf.server.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.device.notification.rev221106.SubscribeDeviceNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.device.notification.rev221106.SubscribeDeviceNotificationInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.device.notification.rev221106.SubscribeDeviceNotificationOutput;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.stmt.NotificationEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

/**
 * RESTCONF implementation of {@link SubscribeDeviceNotification}.
 */
// FIXME: this should be a component
public final class SubscribeDeviceNotificationRpc extends RpcImplementation {
    private static final NodeIdentifier DEVICE_NOTIFICATION_PATH_NODEID =
        NodeIdentifier.create(QName.create(SubscribeDeviceNotificationInput.QNAME, "path").intern());
    // FIXME: NETCONF-1102: this should be 'stream-name'
    private static final NodeIdentifier DEVICE_NOTIFICATION_STREAM_PATH_NODEID =
        NodeIdentifier.create(QName.create(SubscribeDeviceNotificationInput.QNAME, "stream-path").intern());

    private final DOMMountPointService mountPointService;
    private final ListenersBroker broker;

    public SubscribeDeviceNotificationRpc(final ListenersBroker broker, final DOMMountPointService mountPointService) {
        super(SubscribeDeviceNotification.QNAME);
        this.mountPointService = requireNonNull(mountPointService);
        this.broker = requireNonNull(broker);
    }

    @Override
    public RestconfFuture<RpcOutput> invoke(final Principal principal, final String restconfUri, final RpcInput input) {
        final var databind = input.databind();
        final var body = input.input();

        final var pathLeaf = body.childByArg(DEVICE_NOTIFICATION_PATH_NODEID);
        if (pathLeaf == null) {
            return RestconfFuture.failed(new RestconfDocumentedException("No path specified", ErrorType.APPLICATION,
                ErrorTag.MISSING_ELEMENT));
        }
        final var pathLeafBody = pathLeaf.body();
        if (!(pathLeafBody instanceof YangInstanceIdentifier path)) {
            return RestconfFuture.failed(new RestconfDocumentedException("Unexpected path " + pathLeafBody,
                ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT));
        }
        if (!(path.getLastPathArgument() instanceof NodeIdentifierWithPredicates listId)) {
            return RestconfFuture.failed(new RestconfDocumentedException(path + " does not refer to a list item",
                ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT));
        }
        if (listId.size() != 1) {
            return RestconfFuture.failed(new RestconfDocumentedException(path + " uses multiple keys",
                ErrorType.APPLICATION, ErrorTag.INVALID_VALUE));
        }

        final DOMMountPoint mountPoint = mountPointService.getMountPoint(path)
            .orElseThrow(() -> new RestconfDocumentedException("Mount point not available", ErrorType.APPLICATION,
                ErrorTag.OPERATION_FAILED));

        final DOMNotificationService mountNotifService = mountPoint.getService(DOMNotificationService.class)
            .orElseThrow(() -> new RestconfDocumentedException("Mount point does not support notifications",
                ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED));

        final var mountModelContext = mountPoint.getService(DOMSchemaService.class)
            .orElseThrow(() -> new RestconfDocumentedException("Mount point schema not available",
                ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED))
            .getGlobalContext();
        final var notificationPaths = mountModelContext.getModuleStatements().values().stream()
            .flatMap(module -> module.streamEffectiveSubstatements(NotificationEffectiveStatement.class))
            .map(notification -> Absolute.of(notification.argument()))
            .collect(ImmutableSet.toImmutableSet());
        if (notificationPaths.isEmpty()) {
            throw new RestconfDocumentedException("Device does not support notification", ErrorType.APPLICATION,
                ErrorTag.OPERATION_FAILED);
        }

        return broker.createStream("All YANG notifications occuring on mount point /"
            + IdentifierCodec.serialize(path, databind.modelContext()), restconfUri,
            streamName -> new DeviceNotificationStream(broker, streamName, null, mountModelContext,
                mountPointService, mountPoint.getIdentifier()))
            .transform(stream -> {
                stream.listen(mountNotifService, notificationPaths);
                return new RpcOutput(databind, Builders.containerBuilder()
                    .withNodeIdentifier(new NodeIdentifier(SubscribeDeviceNotificationOutput.QNAME))
                    .withChild(ImmutableNodes.leafNode(DEVICE_NOTIFICATION_STREAM_PATH_NODEID, stream.name()))
                    .build());
            });
    }
}
