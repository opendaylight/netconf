/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Optional;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.restconf.server.api.DataGetParams;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public interface DataOperationService {
    ListenableFuture<? extends DOMRpcResult> createData(Data path, NormalizedNode data);

    ListenableFuture<? extends DOMRpcResult> deleteData(Data path);

    ListenableFuture<? extends DOMRpcResult> removeData(Data path);

    ListenableFuture<? extends DOMRpcResult> mergeData(Data path, NormalizedNode data);

    ListenableFuture<? extends DOMRpcResult> putData(Data path, NormalizedNode data);

    ListenableFuture<Optional<NormalizedNode>> getData(Data path, DataGetParams params);

    ListenableFuture<? extends DOMRpcResult> commit();
}
