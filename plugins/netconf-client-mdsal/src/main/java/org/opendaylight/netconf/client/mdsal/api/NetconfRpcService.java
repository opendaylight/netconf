/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import com.google.common.util.concurrent.ListenableFuture;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMRpcException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMService;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.YangConstants;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

/**
 * A {@link DOMService} capturing the ability to invoke RPCs which are defined in RFC4741 and in RFC6241.
 */
public interface NetconfRpcService extends DOMService {
    /**
     * Invoke a base RFC4741/RFC6241 RPC, e.g. those in {@link YangConstants#NETCONF_NAMESPACE}.
     *
     * @param type QName of the RPC to be invoked
     * @param input Input arguments, null if the RPC does not take any.
     * @return A {@link ListenableFuture} which will return either a result structure, or report a subclass
     *         of {@link DOMRpcException} reporting a transport error.
     */
    @NonNull ListenableFuture<? extends DOMRpcResult> invokeNetconf(@NonNull QName type, @NonNull ContainerNode input);
}
