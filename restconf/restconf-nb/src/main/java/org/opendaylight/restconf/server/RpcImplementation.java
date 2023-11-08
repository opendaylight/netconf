/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;

/**
 * An implementation of a YANG-defined RPC.
 */
@NonNullByDefault
public abstract class RpcImplementation {
    /**
     * Input of {@link RpcImplementation#invoke(Principal, String, RpcInput)}.
     */
    public record RpcInput(DatabindContext databind, ContainerNode input) {
        public RpcInput {
            requireNonNull(databind);
            requireNonNull(input);
        }
    }

    /**
     * Output of {@link RpcImplementation#invoke(Principal, String, RpcInput)}.
     */
    public record RpcOutput(DatabindContext databind, @Nullable ContainerNode output) {
        public RpcOutput {
            requireNonNull(databind);
        }
    }

    private final QName qname;

    protected RpcImplementation(final QName qname) {
        this.qname = requireNonNull(qname);
    }

    /**
     * Return the RPC name, as defined by {@code rpc} statement's argument.
     *
     * @return The RPC name
     */
    public final QName qname() {
        return qname;
    }

    /**
     * Asynchronously invoke this implementation. Implementations are expected to report all results via the returned
     * future, e.g. not throw exceptions.
     *
     * @param principal Request principal
     * @param restconfUri Request URI trimmed to the root RESTCONF endpoint
     * @param input RPC input
     * @return Future RPC output
     */
    public abstract RestconfFuture<RpcOutput> invoke(Principal principal, String restconfUri, RpcInput input);

    @Override
    public final String toString() {
        return MoreObjects.toStringHelper(this).add("qname", qname).toString();
    }

    protected static final <T> @Nullable T leaf(final ContainerNode parent, final NodeIdentifier arg,
            final Class<T> type) {
        final var child = parent.childByArg(arg);
        if (child instanceof LeafNode<?> leafNode) {
            final var body = leafNode.body();
            try {
                return type.cast(body);
            } catch (ClassCastException e) {
                throw new IllegalArgumentException("Bad child " + child.prettyTree(), e);
            }
        }
        return null;
    }
}
