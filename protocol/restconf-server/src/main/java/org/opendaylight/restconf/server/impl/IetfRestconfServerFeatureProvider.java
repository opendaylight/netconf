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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.server.rev240814.HttpListen;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.server.rev240814.HttpsListen;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.server.rev240814.IetfRestconfServerData;
import org.opendaylight.yangtools.binding.YangFeature;
import org.opendaylight.yangtools.binding.meta.YangFeatureProvider;

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
       // FIXME add HttpsCallHome.VALUE when server refactoring is complete
        return Set.of(HttpListen.VALUE, HttpsListen.VALUE);
    }
}
