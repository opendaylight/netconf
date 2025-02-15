/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.Executor;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.DeviceNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;

// FIXME: everything except DeviceActionFactory is mandatory, hence this builder is a superfluous indirection
public final class NetconfDeviceBuilder {
    private boolean reconnectOnSchemasChange;
    private DeviceNetconfSchemaProvider deviceSchemaProvider;
    private RemoteDeviceId id;
    private RemoteDeviceHandler salFacade;
    // FIXME: this should not be here
    private Executor processingExecutor;
    private DeviceActionFactory deviceActionFactory;
    private BaseNetconfSchemaProvider baseSchemaProvider;
    private boolean isNetconfStreamNotificationsEnabled;

    public @NonNull NetconfDeviceBuilder setReconnectOnSchemasChange(final boolean reconnectOnSchemasChange) {
        this.reconnectOnSchemasChange = reconnectOnSchemasChange;
        return this;
    }

    public @NonNull NetconfDeviceBuilder setId(final RemoteDeviceId id) {
        this.id = requireNonNull(id);
        return this;
    }

    public @NonNull NetconfDeviceBuilder setSalFacade(final RemoteDeviceHandler salFacade) {
        this.salFacade = requireNonNull(salFacade);
        return this;
    }

    public @NonNull NetconfDeviceBuilder setProcessingExecutor(final Executor processingExecutor) {
        this.processingExecutor = requireNonNull(processingExecutor);
        return this;
    }

    public @NonNull NetconfDeviceBuilder setDeviceActionFactory(final DeviceActionFactory deviceActionFactory) {
        this.deviceActionFactory = deviceActionFactory;
        return this;
    }

    public @NonNull NetconfDeviceBuilder setBaseSchemaProvider(final BaseNetconfSchemaProvider baseSchemaProvider) {
        this.baseSchemaProvider = requireNonNull(baseSchemaProvider);
        return this;
    }

    public @NonNull NetconfDeviceBuilder setDeviceSchemaProvider(
            final DeviceNetconfSchemaProvider deviceSchemaProvider) {
        this.deviceSchemaProvider = requireNonNull(deviceSchemaProvider);
        return this;
    }

    public @NonNull NetconfDeviceBuilder setNetconfStreamNotificationsEnabled(
            final boolean netconfStreamNotificationsEnabled) {
        this.isNetconfStreamNotificationsEnabled = netconfStreamNotificationsEnabled;
        return this;
    }

    public @NonNull NetconfDevice build() {
        return new NetconfDevice(
            requireNonNull(id, "RemoteDeviceId is not initialized"),
            requireNonNull(baseSchemaProvider, "BaseNetconfSchemaProvider is not initialized"),
            requireNonNull(deviceSchemaProvider, "DeviceNetconfSchemaProvider is not initialized"),
            requireNonNull(salFacade, "RemoteDeviceHandler is not initialized"),
            requireNonNull(processingExecutor, "Executor is not initialized"),
            reconnectOnSchemasChange, deviceActionFactory, isNetconfStreamNotificationsEnabled);
    }
}
