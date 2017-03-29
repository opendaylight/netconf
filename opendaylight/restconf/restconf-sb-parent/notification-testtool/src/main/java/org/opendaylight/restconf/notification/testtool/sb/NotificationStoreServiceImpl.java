/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.notification.testtool.sb;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.Future;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.notification.store.rev160506.NotificationStoreService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.notification.store.rev160506.Notifications;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.notification.store.rev160506.ResetInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.notification.store.rev160506.notifications.Device;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.notification.store.rev160506.notifications.DeviceKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

public class NotificationStoreServiceImpl implements NotificationStoreService, BindingAwareProvider, AutoCloseable {

    private DataBroker dataBroker;
    private BindingAwareBroker.RpcRegistration<NotificationStoreService> registration;

    @Override
    public void onSessionInitiated(BindingAwareBroker.ProviderContext providerContext) {
        this.dataBroker = providerContext.getSALService(DataBroker.class);
        registration = providerContext.addRpcImplementation(NotificationStoreService.class, this);
    }

    @Override
    public Future<RpcResult<Void>> reset(ResetInput input) {
        final ReadWriteTransaction tx = dataBroker.newReadWriteTransaction();
        final InstanceIdentifier<Device> id = InstanceIdentifier.create(Notifications.class)
                .child(Device.class, new DeviceKey(input.getDeviceId()));
        final CheckedFuture<Optional<Device>, ReadFailedException> readResult = tx.read(LogicalDatastoreType.OPERATIONAL, id);
        final ListenableFuture<Void> deleteFuture = Futures.transform(readResult, new AsyncFunction<Optional<Device>, Void>() {
            @Override
            public ListenableFuture<Void> apply(Optional<Device> input) throws Exception {
                if (input.isPresent()) {
                    tx.delete(LogicalDatastoreType.OPERATIONAL, id);
                    return tx.submit();
                } else {
                    return Futures.immediateFuture(null);
                }
            }
        });
        return Futures.transform(deleteFuture, new Function<Void, RpcResult<Void>>() {
            @Nullable
            @Override
            public RpcResult<Void> apply(@Nullable Void input) {
                return RpcResultBuilder.<Void>success().build();
            }
        });
    }

    @Override
    public void close() throws Exception {
        registration.close();
    }

}
