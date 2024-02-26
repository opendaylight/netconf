/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.source.SourceRepresentation;
import org.opendaylight.yangtools.yang.model.api.stmt.FeatureSet;

/**
 * The specification of the {@link SourceRepresentation}s and how they need to be assembled to form the device's
 * {@link EffectiveModelContext}.
 *
 * @param requiredSources the set of sources that are required to form the accurate model of the device
 * @param librarySources additional sources that should be presented to YANG parser, typically to resolve submodules
 *                       when the originator of this object is Just Not Sure(tm)
 * @param providedSources {@link ProvidedSources} grouped by their representation
 */
// FIXME: this structure will need to be updated for NMDA to support per-datastore model contexts
@NonNullByDefault
public record NetconfDeviceSchemas(
        // FIXME: NETCONF-840: use SourceIdentifier
        Set<QName> requiredSources,
        FeatureSet features,
        // FIXME: NETCONF-840: use SourceIdentifier
        Set<QName> librarySources,
        List<ProvidedSources<?>> providedSources) {
    public NetconfDeviceSchemas {
        requiredSources = Set.copyOf(requiredSources);
        requireNonNull(features);
        librarySources = Set.copyOf(librarySources);
        providedSources = List.copyOf(providedSources);
    }
}
