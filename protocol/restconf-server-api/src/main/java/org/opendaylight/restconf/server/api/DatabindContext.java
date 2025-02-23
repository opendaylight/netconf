/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.common.ErrorMessage;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangNetconfErrorAware;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactory;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlCodecFactory;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * An immutable context holding a consistent view of things related to data bind operations.
 */
public final class DatabindContext {
    private static final VarHandle JSON_CODECS;
    private static final VarHandle XML_CODECS;
    private static final VarHandle SCHEMA_TREE;

    static {
        final var lookup = MethodHandles.lookup();
        try {
            JSON_CODECS = lookup.findVarHandle(DatabindContext.class, "jsonCodecs", JSONCodecFactory.class);
            XML_CODECS = lookup.findVarHandle(DatabindContext.class, "xmlCodecs", XmlCodecFactory.class);
            SCHEMA_TREE = lookup.findVarHandle(DatabindContext.class, "schemaTree", DataSchemaContextTree.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final @NonNull MountPointContext mountContext;

    @SuppressWarnings("unused")
    @SuppressFBWarnings(value = "UUF_UNUSED_FIELD", justification = "https://github.com/spotbugs/spotbugs/issues/2749")
    private volatile DataSchemaContextTree schemaTree;
    @SuppressWarnings("unused")
    @SuppressFBWarnings(value = "UUF_UNUSED_FIELD", justification = "https://github.com/spotbugs/spotbugs/issues/2749")
    private volatile JSONCodecFactory jsonCodecs;
    @SuppressWarnings("unused")
    @SuppressFBWarnings(value = "UUF_UNUSED_FIELD", justification = "https://github.com/spotbugs/spotbugs/issues/2749")
    private volatile XmlCodecFactory xmlCodecs;

    private DatabindContext(final @NonNull MountPointContext mountContext) {
        this.mountContext = requireNonNull(mountContext);
    }

    public static @NonNull DatabindContext ofModel(final EffectiveModelContext modelContext) {
        return ofMountPoint(MountPointContext.of(modelContext));
    }

    public static @NonNull DatabindContext ofMountPoint(final MountPointContext mountContext) {
        return new DatabindContext(mountContext);
    }

    public @NonNull EffectiveModelContext modelContext() {
        return mountContext.modelContext();
    }

    public @NonNull DataSchemaContextTree schemaTree() {
        final var existing = (DataSchemaContextTree) SCHEMA_TREE.getAcquire(this);
        return existing != null ? existing : createSchemaTree();
    }

    private @NonNull DataSchemaContextTree createSchemaTree() {
        final var created = DataSchemaContextTree.from(modelContext());
        final var witness = (DataSchemaContextTree) SCHEMA_TREE.compareAndExchangeRelease(this, null, created);
        return witness != null ? witness : created;
    }

    public @NonNull JSONCodecFactory jsonCodecs() {
        final var existing = (JSONCodecFactory) JSON_CODECS.getAcquire(this);
        return existing != null ? existing : createJsonCodecs();
    }

    private @NonNull JSONCodecFactory createJsonCodecs() {
        final var created = JSONCodecFactorySupplier.RFC7951.getShared(mountContext.modelContext());
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

    /**
     * Return a new {@link ServerException} constructed from the combination of a message and a caught exception.
     * Provided exception and its causal chain will be examined for well-known constructs in an attempt to extract
     * error information. If no such information is found an error with type {@link ErrorType#PROTOCOL} and tag
     * {@link ErrorTag#MALFORMED_MESSAGE} will be reported.
     *
     * @param messagePrefix exception message prefix
     * @param caught caught exception
     * @return A new {@link ServerException}
     */
    @NonNullByDefault
    public ServerException newProtocolMalformedMessageServerException(final String messagePrefix,
            final Exception caught) {
        return newServerParseException(ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE, messagePrefix, caught);
    }

    @NonNullByDefault
    private ServerException newServerParseException(final ErrorType type, final ErrorTag tag,
            final String messagePrefix, final Exception caught) {
        final var message = requireNonNull(messagePrefix) + ": " + caught.getMessage();
        final var errors = exceptionErrors(caught);
        return new ServerException(message, errors != null ? errors : List.of(new ServerError(type, tag, message)),
            caught);
    }

    private @Nullable List<@NonNull ServerError> exceptionErrors(final Exception caught) {
        Throwable cause = caught;
        do {
            if (cause instanceof YangNetconfErrorAware infoAware) {
                return infoAware.getNetconfErrors().stream()
                    .map(error -> {
                        final var message = error.message();
                        final var path = error.path();

                        return new ServerError(error.type(), error.tag(),
                            message != null ? new ErrorMessage(message) : null, error.appTag(),
                            path != null ? new ServerErrorPath(this, path) : null,
                            // FIXME: pass down error.info()
                            null);
                    })
                    .collect(Collectors.toList());
            }
            cause = cause.getCause();
        } while (cause != null);

        return null;
    }
}
