/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.server.RpcImplementation.RpcInput;
import org.opendaylight.restconf.server.RpcImplementation.RpcOutput;

/**
 *
 */
@NonNullByDefault
public interface RestconfServer {

    RestconfFuture<RpcOutput> invoke(Principal principal, String restconfUri, RpcInput input);
}
