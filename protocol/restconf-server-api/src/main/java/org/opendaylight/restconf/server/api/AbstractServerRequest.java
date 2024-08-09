/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.UUID;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for {@link ServerRequest} implementations. Each instance is automatically assigned a
 * <a href="https://www.rfc-editor.org/rfc/rfc4122#section-4.4">type 4 UUID</a>.
 *
 * @param <T> type of reported result
 */
public abstract non-sealed class AbstractServerRequest<T> implements ServerRequest<T> {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractServerRequest.class);

    private static final VarHandle UUID_VH;

    static {
        try {
            UUID_VH = MethodHandles.lookup().findVarHandle(AbstractServerRequest.class, "uuid", UUID.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final @NonNull QueryParameters queryParameters;
    private final @NonNull PrettyPrintParam prettyPrint;

    @SuppressFBWarnings(value = "UUF_UNUSED_FIELD", justification = "https://github.com/spotbugs/spotbugs/issues/2749")
    @SuppressWarnings("unused")
    private volatile UUID uuid;

    // TODO: this is where a binding to security principal and access control should be:
    //       - we would like to be able to have java.security.Principal#name() for logging purposes
    //       - we need to have a NACM-capable interface, through which we can check permissions (such as data PUT) and
    //         establish output filters (i.e. excluding paths inaccessible path to user from a data GET a ContainerNode)

    /**
     * Default constructor. It takes user-supplied {@link QueryParameters} and a set of default implemented parameter
     * values (for now only {@link PrettyPrintParam}). It implemented parameters are stripped from the user-supplied
     * ones, hence not exposing to {@link RestconfServer} implementation.
     *
     * @param queryParameters user-supplied query parameters
     * @param defaultPrettyPrint default {@link PrettyPrintParam}
     */
    @NonNullByDefault
    protected AbstractServerRequest(final QueryParameters queryParameters, final PrettyPrintParam defaultPrettyPrint) {
        // We always recognize PrettyPrintParam and it is an output processing flag. We therefore filter it if present.
        final var tmp = queryParameters.lookup(PrettyPrintParam.uriName, PrettyPrintParam::forUriValue);
        if (tmp != null) {
            this.queryParameters = queryParameters.withoutParam(PrettyPrintParam.uriName);
            prettyPrint = tmp;
        } else {
            this.queryParameters = queryParameters;
            prettyPrint = requireNonNull(defaultPrettyPrint);
        }
    }

    @Override
    public final UUID uuid() {
        final var existing = (UUID) UUID_VH.getAcquire(this);
        return existing != null ? existing : loadUuid();
    }

    private @NonNull UUID loadUuid() {
        final var created = UUID.randomUUID();
        final var witness = (UUID) UUID_VH.compareAndExchangeRelease(this, null, created);
        return witness != null ? witness : created;
    }

    @Override
    public final QueryParameters queryParameters() {
        return queryParameters;
    }

    @Override
    public final void completeWith(final T result) {
        onSuccess(requireNonNull(result));
    }

    @Override
    public final void completeWith(final ServerException failure) {
        LOG.debug("Request {} failed", this, failure);
        final var errors = failure.errors();
        onFailure(new YangErrorsBody(errors));
    }

    @Override
    public final void completeWith(final YangErrorsBody errors) {
        onFailure(requireNonNull(errors));
    }

    /**
     * Return the effective {@link PrettyPrintParam}.
     *
     * @return the effective {@link PrettyPrintParam}
     */
    protected final @NonNull PrettyPrintParam prettyPrint() {
        return prettyPrint;
    }

    protected abstract void onSuccess(@NonNull T result);

    protected abstract void onFailure(@NonNull YangErrorsBody errors);

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this)).toString();
    }

    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        helper.add("uuid", uuid());

        final var principal = principal();
        if (principal != null) {
            helper.add("principal", principal.getName());
        }
        return helper
            .add("parameters", queryParameters)
            .add("prettyPrint", prettyPrint);
    }
}
