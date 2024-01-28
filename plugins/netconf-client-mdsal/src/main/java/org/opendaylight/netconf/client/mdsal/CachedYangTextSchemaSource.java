/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.api.source.YangTextSource;
import org.opendaylight.yangtools.yang.model.spi.source.StringYangTextSource;

/**
 * A {@link YangTextSource} cached from a remote service.
 */
public final class CachedYangTextSchemaSource extends StringYangTextSource {
    private final @NonNull RemoteDeviceId deviceId;

    public CachedYangTextSchemaSource(final RemoteDeviceId deviceId, final SourceIdentifier sourceId,
            final String content, final String symbolicName) {
        super(sourceId, content, symbolicName);
        this.deviceId = requireNonNull(deviceId);
    }

    public @NonNull RemoteDeviceId deviceId() {
        return deviceId;
    }

    @Override
    protected MoreObjects.ToStringHelper addToStringAttributes(final MoreObjects.ToStringHelper toStringHelper) {
        return super.addToStringAttributes(toStringHelper.add("deviceId", deviceId));
    }
}
