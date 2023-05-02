/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ListenableFuture;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Actions;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

/**
 * Set of interfaces exposed by a {@link RemoteDevice}.
 */
public record RemoteDeviceServices(@NonNull Rpcs rpcs, @Nullable Actions actions) {
    public RemoteDeviceServices {
        requireNonNull(rpcs);
    }

    /**
     * Interface exposing NETCONF device RPC service. This interface is never implemented directly, but rather through
     * its {@code non-sealed} specializations.
     */
    public sealed interface Rpcs extends NetconfRpcService permits Rpcs.Normalized, Rpcs.Schemaless {
        /**
         * NETCONF device RPCs operating just as any other {@link DOMRpcService}.
         */
        non-sealed interface Normalized extends Rpcs, DOMRpcService {
            @Override
            default ListenableFuture<? extends DOMRpcResult> invokeNetconf(final QName type,
                    final ContainerNode input) {
                return invokeRpc(type, input);
            }
        }

        /**
         * NETCONF device RPCs operating in terms of {@link SchemalessRpcService}.
         */
        non-sealed interface Schemaless extends Rpcs, SchemalessRpcService {
            // Just an interface combination
        }
    }

    /**
     * Interface exposing NETCONF device Action service. This interface is never implemented directly, but rather
     * through its {@code non-sealed} specializations.
     */
    public sealed interface Actions permits Actions.Normalized {
        /**
         * NETCONF device RPCs operating just as any other {@link DOMActionService}.
         */
        non-sealed interface Normalized extends Actions, DOMActionService {
            // Just an interface combination
        }
    }
}
