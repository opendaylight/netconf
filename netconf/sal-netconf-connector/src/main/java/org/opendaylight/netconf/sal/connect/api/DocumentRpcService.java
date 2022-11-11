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
import org.opendaylight.mdsal.dom.api.DOMService;
import org.w3c.dom.Document;

/**
 * A {@link DOMService} exposing RPC invocation model based on {@link Document}s.
 */
@Beta
public interface DocumentRpcService extends DOMService {

    ListenableFuture<? extends Document> invokeRpc(@NonNull String namespaceURI, @NonNull String localName,
            Document input);
}
