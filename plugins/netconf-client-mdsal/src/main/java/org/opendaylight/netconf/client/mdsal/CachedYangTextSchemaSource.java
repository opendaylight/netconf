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
import java.io.Reader;
import java.io.StringReader;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.api.source.YangTextSource;

/**
 * A {@link YangTextSource} cached from a remote service.
 */
// FIXME: subclass StringYangTextSource and cleanup addToStringAttributes()
public final class CachedYangTextSchemaSource extends YangTextSource {
    private final @NonNull SourceIdentifier sourceId;
    private final @NonNull RemoteDeviceId id;
    private final @NonNull String symbolicName;
    private final @NonNull String schemaString;

    public CachedYangTextSchemaSource(final RemoteDeviceId id, final SourceIdentifier sourceId,
            final String symbolicName, final String schemaString) {
        this.id = requireNonNull(id);
        this.sourceId = requireNonNull(sourceId);
        this.symbolicName = requireNonNull(symbolicName);
        this.schemaString = requireNonNull(schemaString);
    }

    @Override
    public Reader openStream() {
        return new StringReader(schemaString);
    }

    @Override
    public SourceIdentifier sourceId() {
        return sourceId;
    }

    @Override
    public String symbolicName() {
        return symbolicName;
    }

    @Override
    protected MoreObjects.ToStringHelper addToStringAttributes(final MoreObjects.ToStringHelper toStringHelper) {
        return toStringHelper.add("device", id);
    }
}