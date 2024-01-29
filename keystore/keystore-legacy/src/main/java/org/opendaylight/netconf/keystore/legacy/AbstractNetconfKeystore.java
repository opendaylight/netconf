/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.legacy;

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017._private.keys.PrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.trusted.certificates.TrustedCertificate;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Abstract substrate for implementing security services based on the contents of {@link Keystore}.
 */
public abstract class AbstractNetconfKeystore {
    @NonNullByDefault
    protected record State(
            Map<String, PrivateKey> privateKeys,
            Map<String, TrustedCertificate> trustedCertificates) implements Immutable {
        public static final State EMPTY = new State(Map.of(), Map.of());

        public State {
            privateKeys = Map.copyOf(privateKeys);
            trustedCertificates = Map.copyOf(trustedCertificates);
        }
    }

    @NonNullByDefault
    private record ConfigState(
            Map<String, PrivateKey> privateKeys,
            Map<String, TrustedCertificate> trustedCertificates) implements Immutable {
        static final ConfigState EMPTY = new ConfigState(Map.of(), Map.of());

        ConfigState {
            privateKeys = Map.copyOf(privateKeys);
            trustedCertificates = Map.copyOf(trustedCertificates);
        }
    }

    @NonNullByDefault
    record ConfigStateBuilder(
            HashMap<String, PrivateKey> privateKeys,
            HashMap<String, TrustedCertificate> trustedCertificates) {
        ConfigStateBuilder {
            requireNonNull(privateKeys);
            requireNonNull(trustedCertificates);
        }
    }

    private final AtomicReference<@NonNull ConfigState> state = new AtomicReference<>(ConfigState.EMPTY);

    private @Nullable Registration configListener;

    protected final void start(final DataBroker dataBroker) {
        if (configListener == null) {
            configListener = dataBroker.registerTreeChangeListener(
                DataTreeIdentifier.of(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Keystore.class)),
                new ConfigListener(this));
        }
    }

    protected final void stop() {
        final var listener = configListener;
        if (listener != null) {
            configListener = null;
            listener.close();
            state.set(ConfigState.EMPTY);
        }
    }

    protected abstract void onStateUpdated(@NonNull State newState);

    final void runUpdate(final Consumer<@NonNull ConfigStateBuilder> task) {
        final var prevState = state.getAcquire();

        final var builder = new ConfigStateBuilder(new HashMap<>(prevState.privateKeys),
            new HashMap<>(prevState.trustedCertificates));
        task.accept(builder);
        final var newState = new ConfigState(builder.privateKeys, builder.trustedCertificates);

        // Careful application -- check if listener is still up and whether the state was not updated.
        if (configListener == null || state.compareAndExchangeRelease(prevState, newState) != prevState) {
            return;
        }

        // FIXME: compile to crypto

        onStateUpdated(new State(newState.privateKeys, newState.trustedCertificates));

        // FIXME: tickle operational updater (which does not exist yet)
    }
}
