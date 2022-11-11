/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.api;

import com.google.common.annotations.Beta;
import com.google.common.util.concurrent.ListenableFuture;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMService;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * A {@link DOMService} exposing RPC invocation model based either {@code ContainerNode} or {@code AnyxmlNode}.
 */
@Beta
public interface SchemalessRpcService extends DOMService {
    // FIXME: NETCONF-669: DOMRpcResult and NormalizedNode does not work yangtools-10, at which point we need to provide
    //                     our own structure for result. The shape of those structure is TBD as we untangle
    //                     the transformers.
    @NonNull ListenableFuture<? extends DOMRpcResult> invokeRpc(@NonNull QName type, @NonNull NormalizedNode input);
}
