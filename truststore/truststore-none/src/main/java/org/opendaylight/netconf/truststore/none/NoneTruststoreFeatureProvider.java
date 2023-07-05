/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.truststore.none;

import java.util.Set;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.kohsuke.MetaInfServices;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev230417.IetfTruststoreData;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev230417.InlineDefinitionsSupported;
import org.opendaylight.yangtools.yang.binding.YangFeature;
import org.opendaylight.yangtools.yang.binding.YangFeatureProvider;

/**
 * Simple implementation, which advertizes we do not have a central truststore.
 */
@MetaInfServices
@NonNullByDefault
public final class NoneTruststoreFeatureProvider implements YangFeatureProvider<IetfTruststoreData> {
    @Override
    public Class<IetfTruststoreData> boundModule() {
        return IetfTruststoreData.class;
    }

    @Override
    public Set<? extends YangFeature<?, IetfTruststoreData>> supportedFeatures() {
        return Set.of(InlineDefinitionsSupported.VALUE);
    }
}
