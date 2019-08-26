/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.Collections2;
import java.util.Collection;
import java.util.Set;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.repo.api.RevisionSourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;

/**
 * Contains RequiredSources - sources from capabilities.
 */
final class DeviceSources {
    private final Set<QName> requiredSources;
    private final Set<QName> providedSources;
    private final SchemaSourceProvider<YangTextSchemaSource> sourceProvider;

    DeviceSources(final Set<QName> requiredSources, final Set<QName> providedSources,
            final SchemaSourceProvider<YangTextSchemaSource> sourceProvider) {
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

    Collection<SourceIdentifier> getRequiredSources() {
        return Collections2.transform(requiredSources, DeviceSources::toSourceId);
    }

    Collection<SourceIdentifier> getProvidedSources() {
        return Collections2.transform(providedSources, DeviceSources::toSourceId);
    }

    SchemaSourceProvider<YangTextSchemaSource> getSourceProvider() {
        return sourceProvider;
    }

    private static SourceIdentifier toSourceId(final QName input) {
        return RevisionSourceIdentifier.create(input.getLocalName(), input.getRevision());
    }
}