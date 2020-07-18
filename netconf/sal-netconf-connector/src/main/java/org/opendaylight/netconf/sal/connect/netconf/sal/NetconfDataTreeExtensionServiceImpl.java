/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.dom.api.NetconfDataTreeExtensionService;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfRpcFutureCallback;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.xpath.NetconfXPathContext;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public class NetconfDataTreeExtensionServiceImpl implements NetconfDataTreeExtensionService {

    private final NetconfBaseOps netconfOps;
    private final RemoteDeviceId id;

    public NetconfDataTreeExtensionServiceImpl(NetconfBaseOps netconfOps, RemoteDeviceId id) {
        this.netconfOps = netconfOps;
        this.id = id;
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode<?, ?>>> get(final @NonNull NetconfXPathContext xpathContext) {
        return netconfOps.getData(new NetconfRpcFutureCallback("Data read", id), xpathContext);
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode<?, ?>>> getConfig(final @NonNull NetconfXPathContext xpathContext) {
        return netconfOps.getConfigRunningData(new NetconfRpcFutureCallback("Data read", id), xpathContext);
    }

}
