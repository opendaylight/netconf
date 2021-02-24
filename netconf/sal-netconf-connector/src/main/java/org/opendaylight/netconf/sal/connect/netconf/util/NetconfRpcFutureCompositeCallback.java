/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.util;

import com.google.common.util.concurrent.FutureCallback;
import java.util.List;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;

public class NetconfRpcFutureCompositeCallback implements FutureCallback<DOMRpcResult> {

    private final List<FutureCallback<DOMRpcResult>> callbackList;

    public NetconfRpcFutureCompositeCallback(final FutureCallback<DOMRpcResult>... callbacks) {
        callbackList = List.of(callbacks);
    }

    @Override
    public void onSuccess(final DOMRpcResult result) {
        callbackList.forEach(callback -> callback.onSuccess(result));
    }

    @Override
    public void onFailure(final Throwable throwable) {
        callbackList.forEach(callback -> callback.onFailure(throwable));
    }

}
