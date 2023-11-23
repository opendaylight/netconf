/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static java.util.Objects.requireNonNull;

import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactory;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlCodecFactory;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.export.YinExportUtils;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceRepresentation;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.api.YinTextSchemaSource;

/**
 * An immutable context holding a consistent view of things related to data bind operations.
 */
public final class DatabindContext {
    /**
     * Interface for acquiring model source.
     */
    @NonNullByDefault
    @FunctionalInterface
    public interface SourceResolver {
        /**
         * Resolve a specified source into a byte stream in specified representation.
         *
         * @param source Source identifier
         * @param representation Requested representation
         * @return A {@link RestconfFuture} completing with an {@link InputStream}, or {@code null} if the requested
         *        representation is not supported.
         */
        @Nullable RestconfFuture<InputStream> resolveSource(SourceIdentifier source,
            Class<? extends SchemaSourceRepresentation> representation);
    }

    private static final VarHandle JSON_CODECS;
    private static final VarHandle XML_CODECS;

    static {
        final var lookup = MethodHandles.lookup();
        try {
            JSON_CODECS = lookup.findVarHandle(DatabindContext.class, "jsonCodecs", JSONCodecFactory.class);
            XML_CODECS = lookup.findVarHandle(DatabindContext.class, "xmlCodecs", XmlCodecFactory.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final @NonNull MountPointContext mountContext;
    private final SourceResolver sourceResolver;

    @SuppressWarnings("unused")
    private volatile JSONCodecFactory jsonCodecs;
    @SuppressWarnings("unused")
    private volatile XmlCodecFactory xmlCodecs;

    private DatabindContext(final @NonNull MountPointContext mountContext,
            final @Nullable SourceResolver sourceResolver) {
        this.mountContext = requireNonNull(mountContext);
        this.sourceResolver = sourceResolver;
    }

    public static @NonNull DatabindContext ofModel(final EffectiveModelContext modelContext) {
        return ofModel(modelContext, null);
    }

    public static @NonNull DatabindContext ofModel(final EffectiveModelContext modelContext,
            final @Nullable SourceResolver sourceResolver) {
        return ofMountPoint(MountPointContext.of(modelContext), sourceResolver);
    }

    public static @NonNull DatabindContext ofMountPoint(final MountPointContext mountContext) {
        return ofMountPoint(mountContext, null);
    }

    public static @NonNull DatabindContext ofMountPoint(final MountPointContext mountContext,
            final @Nullable SourceResolver sourceResolver) {
        return new DatabindContext(mountContext, sourceResolver);
    }

    public @NonNull EffectiveModelContext modelContext() {
        return mountContext.getEffectiveModelContext();
    }

    public @NonNull JSONCodecFactory jsonCodecs() {
        final var existing = (JSONCodecFactory) JSON_CODECS.getAcquire(this);
        return existing != null ? existing : createJsonCodecs();
    }

    private @NonNull JSONCodecFactory createJsonCodecs() {
        final var created = JSONCodecFactorySupplier.RFC7951.getShared(mountContext.getEffectiveModelContext());
        final var witness = (JSONCodecFactory) JSON_CODECS.compareAndExchangeRelease(this, null, created);
        return witness != null ? witness : created;
    }

    public @NonNull XmlCodecFactory xmlCodecs() {
        final var existing = (XmlCodecFactory) XML_CODECS.getAcquire(this);
        return existing != null ? existing : createXmlCodecs();
    }

    private @NonNull XmlCodecFactory createXmlCodecs() {
        final var created = XmlCodecFactory.create(mountContext);
        final var witness = (XmlCodecFactory) XML_CODECS.compareAndExchangeRelease(this, null, created);
        return witness != null ? witness : created;
    }

    public @NonNull RestconfFuture<InputStream> resolveSource(final SourceIdentifier source,
            final Class<? extends SchemaSourceRepresentation> representation) {
        final var src = requireNonNull(source);
        if (sourceResolver != null) {
            final var delegate = sourceResolver.resolveSource(src, representation);
            if (delegate != null) {
                return delegate;
            }
        }
        if (YangTextSchemaSource.class.isAssignableFrom(representation)) {
            return declaredYangSource(source);
        }
        if (YinTextSchemaSource.class.isAssignableFrom(representation)) {
            return declaredYinSource(source);
        }
        return RestconfFuture.failed(new RestconfDocumentedException(
            "Unsupported source representation " + representation.getName()));
    }

    private @NonNull RestconfFuture<InputStream> declaredYangSource(final SourceIdentifier source) {

    }

    private @NonNull RestconfFuture<InputStream> declaredYinSource(final SourceIdentifier source) {
        YinExportUtils.writeModuleAsYinText(context.module(), entityStream);

    }
}
