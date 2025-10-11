/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.rfc8639.impl;

import java.util.Set;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.kohsuke.MetaInfServices;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeJson$F;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeXml$F;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.IetfSubscribedNotificationsData;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Subtree;
import org.opendaylight.yangtools.binding.YangFeature;
import org.opendaylight.yangtools.binding.meta.YangFeatureProvider;

/**
 * Baseline features supported from {@code ietf-subscribed-notifications.yang}.
 */
@MetaInfServices
@NonNullByDefault
public final class IetfSubscriptionFeatureProvider implements YangFeatureProvider<IetfSubscribedNotificationsData> {
    @Override
    public Class<IetfSubscribedNotificationsData> boundModule() {
        return IetfSubscribedNotificationsData.class;
    }

    @Override
    public Set<? extends YangFeature<?, IetfSubscribedNotificationsData>> supportedFeatures() {
        return Set.of(EncodeJson$F.VALUE, EncodeXml$F.VALUE, Subtree.VALUE);
    }
}
