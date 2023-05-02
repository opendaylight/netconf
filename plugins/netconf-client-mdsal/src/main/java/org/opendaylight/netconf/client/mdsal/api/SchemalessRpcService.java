/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import com.google.common.annotations.Beta;
import com.google.common.util.concurrent.ListenableFuture;
import javax.xml.transform.dom.DOMSource;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMService;
import org.opendaylight.yangtools.yang.common.QName;

/**
 * A {@link DOMService} exposing RPC invocation model based either {@code ContainerNode} or {@code AnyxmlNode}.
 */
@Beta
public interface SchemalessRpcService extends DOMService {

    @NonNull ListenableFuture<? extends DOMSource> invokeRpc(@NonNull QName type, @NonNull DOMSource payload);
}
