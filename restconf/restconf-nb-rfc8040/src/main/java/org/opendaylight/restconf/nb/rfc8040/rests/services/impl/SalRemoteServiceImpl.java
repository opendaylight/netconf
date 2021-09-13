/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.BeginTransactionInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.BeginTransactionOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateDataChangeEventSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateDataChangeEventSubscriptionOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateDataChangeEventSubscriptionOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateNotificationStreamInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateNotificationStreamOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.SalRemoteService;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.CreateDataChangeEventSubscriptionInput1;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.CreateDataChangeEventSubscriptionInput1.Datastore;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

/**
 * Implementation of the subscription model defined in {@link SalRemoteService}.
 */
public final class SalRemoteServiceImpl implements SalRemoteService {
    private static final class DatastoreDTO {
        final LogicalDatastoreType datastoreType;
        final InstanceIdentifier<?> path;
        final NotificationOutputType outputType;

        DatastoreDTO(final LogicalDatastoreType datastoreType, final InstanceIdentifier<?> path,
                final NotificationOutputType outputType) {
            this.datastoreType = requireNonNull(datastoreType);
            this.path = requireNonNull(path);
            this.outputType = requireNonNull(outputType);
        }
    }

    private final ConcurrentMap<UUID, DatastoreDTO> streams = new ConcurrentHashMap<>();

    @Override
    public ListenableFuture<RpcResult<CreateDataChangeEventSubscriptionOutput>> createDataChangeEventSubscription(
            final CreateDataChangeEventSubscriptionInput input) {
        // FIXME: this should never be null, but the model does not support that
        final InstanceIdentifier<?> path = input.getPath();
        final LogicalDatastoreType datastoreType;
        final NotificationOutputType outputType;

        final var aug = input.augmentation(CreateDataChangeEventSubscriptionInput1.class);
        if (aug != null) {
            final var datastore = requireNonNullElse(aug.getDatastore(), Datastore.CONFIGURATION);
            switch (datastore) {
                case CONFIGURATION:
                    datastoreType = LogicalDatastoreType.CONFIGURATION;
                    break;
                case OPERATIONAL:
                    datastoreType = LogicalDatastoreType.OPERATIONAL;
                    break;
                default:
                    throw new IllegalStateException("Unhandled datastore " + datastore);
            }

            outputType = requireNonNullElse(aug.getNotificationOutputType(), NotificationOutputType.XML);
        } else {
            datastoreType = LogicalDatastoreType.CONFIGURATION;
            outputType = NotificationOutputType.XML;
        }

        final var sub = new DatastoreDTO(datastoreType, path, outputType);

        // We are generating a UUID for each created stream
        final String streamName;
        while (true) {
            final UUID uuid = UUID.randomUUID();
            final var existing = streams.putIfAbsent(uuid, sub);
            if (existing == null) {
                streamName = uuid.toString();
                break;
            }
        }

        // registration of the listener
        // FIXME: we need a binding-independent instance-identifier
//        ListenersBroker.getInstance().registerDataChangeListener(path, streamName, outputType);

        return RpcResultBuilder.success(new CreateDataChangeEventSubscriptionOutputBuilder()
            .setStreamName(streamName)
            .build())
            .buildFuture();
    }

    @Override
    public ListenableFuture<RpcResult<CreateNotificationStreamOutput>> createNotificationStream(
            final CreateNotificationStreamInput input) {
        return notImplemented();
    }

    @Override
    public ListenableFuture<RpcResult<BeginTransactionOutput>> beginTransaction(final BeginTransactionInput input) {
        return notImplemented();
    }

    private static <T> ListenableFuture<RpcResult<T>> notImplemented() {
        return RpcResultBuilder.<T>failed()
            .withError(ErrorType.APPLICATION, ErrorTag.OPERATION_NOT_SUPPORTED.elementBody(), "Not implemented yet")
            .buildFuture();
    }
}
