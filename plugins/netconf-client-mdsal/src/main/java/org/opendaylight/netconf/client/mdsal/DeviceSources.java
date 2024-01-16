/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.api.source.YangTextSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;

/**
 * Contains RequiredSources - sources from capabilities.
 */
final class DeviceSources {
    private final Set<QName> requiredSources;
    private final Set<QName> providedSources;
    private final SchemaSourceProvider<YangTextSource> sourceProvider;

    DeviceSources(final Set<QName> requiredSources, final Set<QName> providedSources,
            final SchemaSourceProvider<YangTextSource> sourceProvider) {
        this.requiredSources = requireNonNull(requiredSources);
        this.providedSources = requireNonNull(providedSources);
        this.sourceProvider = requireNonNull(sourceProvider);
    }

    Set<QName> getRequiredSourcesQName() {
        return requiredSources;
    }

    Set<QName> getProvidedSourcesQName() {
        return providedSources;
    }

    List<SourceIdentifier> getRequiredSources() {
        return requiredSources.stream().map(DeviceSources::toSourceId).collect(Collectors.toList());
    }

    List<Registration> register(final SchemaSourceRegistry schemaRegistry) {
        return providedSources.stream()
            .map(DeviceSources::toSourceId)
            .map(sourceId -> schemaRegistry.registerSchemaSource(sourceProvider,
                PotentialSchemaSource.create(sourceId, YangTextSource.class,
                    PotentialSchemaSource.Costs.REMOTE_IO.getValue())))
            .collect(Collectors.toUnmodifiableList());
    }

    private static SourceIdentifier toSourceId(final QName input) {
        return new SourceIdentifier(input.getLocalName(), input.getRevision().map(Revision::toString).orElse(null));
    }
}