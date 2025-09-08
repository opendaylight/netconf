/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.shaded.sshd.common.BaseBuilder;
import org.opendaylight.netconf.shaded.sshd.common.NamedResource;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.TransportParamsGrouping;
import org.opendaylight.yangtools.binding.TypeObject;

/**
 * A policy how to execute an algorithm configuration.
 */
abstract sealed class AlgorithmPolicy<T extends TypeObject, F extends NamedResource>
        permits EncryptionPolicy, KeyExchangePolicy, MacPolicy, PublicKeyPolicy {
    private final @NonNull List<F> defaultFactories;
    private final @NonNull Map<T, F> typeToFactory;

    AlgorithmPolicy(final Map<T, ? extends F> typeToFactory, final List<? extends F> defaultFactories) {
        this.typeToFactory = Map.copyOf(typeToFactory);
        this.defaultFactories = List.copyOf(defaultFactories);
    }

    /**
     * Update a {@link BaseBuilder} to reflect a {@link TransportParamsGrouping} configuration.
     *
     * @param builder the builder
     * @param params optional configuration,
     * @throws UnsupportedConfigurationException if the configuration cannot be applied
     */
    final void setTransportParams(final @NonNull BaseBuilder<?, ?> builder,
            final @Nullable TransportParamsGrouping params) throws UnsupportedConfigurationException {
        setFactories(builder, params == null ? defaultFactories : factoriesOf(algsOf(params)));
    }

    private @NonNull List<F> factoriesOf(final @Nullable List<T> algs) throws UnsupportedConfigurationException {
        if (algs == null || algs.isEmpty()) {
            return defaultFactories;
        }
        final var factories = new ArrayList<F>(algs.size());
        for (var alg : algs) {
            final var factory = typeToFactory.get(requireNonNull(alg));
            if (factory == null) {
                throw new UnsupportedOperationException("Unsupported algorithm " + alg);
            }
            factories.add(factory);
        }
        return List.copyOf(factories);
    }

    abstract @Nullable List<T> algsOf(@NonNull TransportParamsGrouping params);

    abstract void setFactories(@NonNull BaseBuilder<?, ?> builder, @NonNull List<F> factories);

    @VisibleForTesting
    final Stream<F> allFactories() {
        return typeToFactory.values().stream();
    }

    @VisibleForTesting
    final F factoryFor(final T type) {
        return typeToFactory.get(requireNonNull(type));
    }
}
