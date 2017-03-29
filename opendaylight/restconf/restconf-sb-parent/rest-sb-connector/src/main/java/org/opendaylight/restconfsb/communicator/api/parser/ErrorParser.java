/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.api.parser;

import java.util.Collection;
import org.opendaylight.yangtools.yang.common.RpcError;

public interface ErrorParser {
    /**
     * Parses http reply which conforms to errors container defined by ietf-restconf to list of RpcError
     * @param stream http reply stream
     * @return rpc errors
     */
    Collection<RpcError> parseErrors(String stream);
}