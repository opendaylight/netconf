/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.shaded.sshd.common.BaseBuilder;
import org.opendaylight.netconf.shaded.sshd.common.NamedResource;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.TransportParamsGrouping;
import org.opendaylight.yangtools.binding.TypeObject;

/**
 * Base class for supporting type mapping.
 */
abstract class AlgorithmSupport<T extends TypeObject, F extends NamedResource> {
    private final @NonNull List<F> defaultFactories;
    private final @NonNull Map<T, F> typeToFactory;

    AlgorithmSupport(Map<T, F> typeToFactory, List<F> defaultFactories) {
        this.typeToFactory = Map.copyOf(typeToFactory);
        this.defaultFactories = List.copyOf(defaultFactories);
    }

    final void setTransportParams(@NonNull BaseBuilder<?, ?> builder, @Nullable TransportParamsGrouping params)
            throws UnsupportedConfigurationException {
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

    abstract@Nullable List<T> algsOf(final @NonNull TransportParamsGrouping params);

    abstract void setFactories(@NonNull BaseBuilder<?, ?> builder, @NonNull List<F> factories);

//    abstract @NonNull List<F> factoriesFor(@NonNull TransportParamsGrouping params);
}
