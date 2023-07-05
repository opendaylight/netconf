/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.none;

import java.util.Set;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.kohsuke.MetaInfServices;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev230417.AsymmetricKeys;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev230417.IetfKeystoreData;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev230417.InlineDefinitionsSupported;
import org.opendaylight.yangtools.yang.binding.YangFeature;
import org.opendaylight.yangtools.yang.binding.YangFeatureProvider;

/**
 * Simple implementation, which advertizes we do not have a central keystore and support asymmetric keys only.
 */
@MetaInfServices
@NonNullByDefault
public final class NoneKeystoreFeatureProvider implements YangFeatureProvider<IetfKeystoreData> {
    @Override
    public Class<IetfKeystoreData> boundModule() {
        return IetfKeystoreData.class;
    }

    @Override
    public Set<? extends YangFeature<?, IetfKeystoreData>> supportedFeatures() {
        return Set.of(InlineDefinitionsSupported.VALUE, AsymmetricKeys.VALUE);
    }
}
