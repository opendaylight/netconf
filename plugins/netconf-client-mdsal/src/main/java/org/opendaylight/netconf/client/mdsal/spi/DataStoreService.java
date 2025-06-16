/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.Optional;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public interface DataStoreService {

    ListenableFuture<? extends DOMRpcResult> commit();

    ListenableFuture<? extends DOMRpcResult> editConfig(EffectiveOperation operation, NormalizedNode child,
        YangInstanceIdentifier path);

    ListenableFuture<? extends DOMRpcResult> editConfig(EffectiveOperation operation,
        YangInstanceIdentifier path);

    ListenableFuture<? extends DOMRpcResult> editConfig(AnyxmlNode<DOMSource> node);

    ListenableFuture<Optional<NormalizedNode>> read(LogicalDatastoreType store, Data path,
        List<YangInstanceIdentifier> fields);

    ListenableFuture<Optional<NormalizedNode>> read(LogicalDatastoreType store, Data path);
}
