/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import java.util.Set;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.kohsuke.MetaInfServices;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.server.rev230417.IetfRestconfServerData;
import org.opendaylight.yangtools.yang.binding.YangFeature;
import org.opendaylight.yangtools.yang.binding.YangFeatureProvider;

/**
 * Baseline features supported from {@code ietf-restconf-client.yang}.
 */
@MetaInfServices
@NonNullByDefault
public final class IetfRestconfServerFeatureProvider implements YangFeatureProvider<IetfRestconfServerData> {
    @Override
    public Class<IetfRestconfServerData> boundModule() {
        return IetfRestconfServerData.class;
    }

    @Override
    public Set<? extends YangFeature<?, IetfRestconfServerData>> supportedFeatures() {
        // FIXME: support some features
        return Set.of();
    }
}
