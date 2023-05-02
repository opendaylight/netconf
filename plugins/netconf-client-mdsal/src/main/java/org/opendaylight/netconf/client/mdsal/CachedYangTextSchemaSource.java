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
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;

/**
 * A {@link YangTextSchemaSource} cached from a remote service.
 */
public final class CachedYangTextSchemaSource extends YangTextSchemaSource {
    private final RemoteDeviceId id;
    private final byte[] schemaBytes;
    private final String symbolicName;

    public CachedYangTextSchemaSource(final RemoteDeviceId id, final SourceIdentifier sourceIdentifier,
            final String symbolicName, final byte[] schemaBytes) {
        super(sourceIdentifier);
        this.symbolicName = requireNonNull(symbolicName);
        this.id = requireNonNull(id);
        this.schemaBytes = schemaBytes.clone();
    }

    @Override
    protected MoreObjects.ToStringHelper addToStringAttributes(final MoreObjects.ToStringHelper toStringHelper) {
        return toStringHelper.add("device", id);
    }

    @Override
    public InputStream openStream() {
        return new ByteArrayInputStream(schemaBytes);
    }

    @Override
    public Optional<String> getSymbolicName() {
        return Optional.of(symbolicName);
    }
}