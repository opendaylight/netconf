/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import static java.util.Objects.requireNonNull;

import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.api.source.SourceRepresentation;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;

/**
 * A set of sources provided through a single provider. A NETCONF device can have several of these.
 *
 * @param representation the {@link SourceRepresentation} of provided sources
 * @param provider the {@link SchemaSourceProvider} providing the sources
 * @param sources provided sources
 */
@NonNullByDefault
public record ProvidedSources<T extends SourceRepresentation>(
        Class<T> representation,
        SchemaSourceProvider<T> provider,
        // FIXME: NETCONF-840: use SourceIdentifier
        Set<QName> sources) {
    public ProvidedSources {
        representation = requireNonNull(representation);
        provider = requireNonNull(provider);
        sources = Set.copyOf(sources);
    }

    public Stream<Registration> registerWith(final SchemaSourceRegistry registry, final int cost) {
        return sources.stream()
            .map(qname -> new SourceIdentifier(qname.getLocalName(), qname.getModule().revision()))
            .map(sourceId -> registry.registerSchemaSource(provider,
                PotentialSchemaSource.create(sourceId, representation, cost)));
    }
}
