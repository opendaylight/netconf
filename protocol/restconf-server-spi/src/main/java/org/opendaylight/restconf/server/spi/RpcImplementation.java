/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;

/**
 * An implementation of a YANG-defined RPC.
 */
@NonNullByDefault
public abstract class RpcImplementation {
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
     * @param request {@link ServerRequest} for this invocation.
     * @param restconfURI Request URI trimmed to the root RESTCONF endpoint, resolved {@code {+restconf}} resource name
     * @param input RPC input
     */
    public abstract void invoke(ServerRequest<ContainerNode> request, URI restconfURI, OperationInput input);

    @Override
    public final String toString() {
        return MoreObjects.toStringHelper(this).add("qname", qname).toString();
    }

    public static final <T> @Nullable T leaf(final DataContainerNode parent, final NodeIdentifier arg,
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
