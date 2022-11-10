/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;
import org.opendaylight.yangtools.rfc8528.data.util.EmptyMountPointContext;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactory;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlCodecFactory;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * An immutable context holding a consistent view of things related to data bind operations.
 */
@NonNullByDefault
public record DatabindContext(MountPointContext mountContext, JSONCodecFactory jsonCodecs, XmlCodecFactory xmlCodecs) {
    public DatabindContext {
        requireNonNull(mountContext);
        requireNonNull(jsonCodecs);
        requireNonNull(xmlCodecs);
    }

    public static DatabindContext ofModel(final EffectiveModelContext modelContext) {
        return ofMountPoint(new EmptyMountPointContext(modelContext));
    }

    public static DatabindContext ofMountPoint(final MountPointContext mountContext) {
        return new DatabindContext(mountContext,
            JSONCodecFactorySupplier.RFC7951.getShared(mountContext.getEffectiveModelContext()),
            XmlCodecFactory.create(mountContext));
    }

    public EffectiveModelContext modelContext() {
        return mountContext.getEffectiveModelContext();
    }
}
