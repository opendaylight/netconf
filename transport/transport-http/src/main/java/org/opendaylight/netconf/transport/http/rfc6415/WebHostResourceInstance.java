/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http.rfc6415;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.base.MoreObjects.ToStringHelper;
import java.net.URI;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.concepts.AbstractRegistration;

/**
 * Abstract base class for {@link WebHostResource} implementations, enhancing the invocation interface with the
 * {@link #close()} clean up contract.
 */
@Beta
public abstract class WebHostResourceInstance extends AbstractRegistration implements WebHostResource {
    // TODO: List<String> path
    private final @NonNull String path;

    protected WebHostResourceInstance(final String path) {
        this.path = requireNonNull(path);
    }

    // TODO: List<String>
    public final @NonNull String path() {
        return path;
    }

    /**
     * The {@link Link} relation type under which this resource should be be published in the {@link XRD}. {@code null}
     * indicates this resource should not be published.
     *
     * @implNote
     *     Default implementation returns {@code null}.
     *
     * @return the {@link Link} relation type, or {@code null}
     */
    public @Nullable URI relationType() {
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Once this method returns, this {@link WebHostResource} methods are considered relinquished and should not be
     * called.
     *
     * <p>Implementations are encouraged to perform reasonable fail-fast behaviour, but the details of such
     * behaviour, including even its presence, are left completely up to each individual implementation.
     *
     * <p>As an example of a valid implementation, we could have the following:
     * <pre>{@code
     *     class FooResource extends WebHostResourceInstance {
     *         private volatile Map<String, Supplier<ByteBuf>> contents;
     *
     *         @Override
     *         public PreparedRequest prepare(ImplementedMethod method, URI targetUri, HttpHeaders headers,
     *                SegmentPeeler peeler, XRD xrd) {
     *             var local = contents;
     *             if (local == null) {
     *                throw new ConcurrentModificationException("closed");
     *             }
     *
     *             // ... peeler checks for self ...
     *
     *             var producer = local.get(peeler.next());
     *             if (producer == null) {
     *                 return AbstractResource.NOT_FOUND;
     *             }
     *
     *             return new BytebufRequestResponse(HttpResponseStatus.OK, producer.get());
     *         }
     *
     *         @Override
     *         protected final removeRegistration() {
     *             contents = null;
     *         }
     *     }
     * }</pre>
     */
    // TODO: convert above javadoc into a snippet which is compiled somewhere
    @Override
    protected abstract void removeRegistration();

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper toStringHelper) {
        return super.addToStringAttributes(toStringHelper.add("path", path));
    }
}
