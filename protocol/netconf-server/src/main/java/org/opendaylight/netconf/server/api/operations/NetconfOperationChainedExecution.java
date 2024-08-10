/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.api.operations;

import org.opendaylight.netconf.api.DocumentedException;
import org.w3c.dom.Document;

/**
 * Single link in netconf operation execution chain. Wraps the execution of a single netconf operation.
 */
public interface NetconfOperationChainedExecution {

    Document execute(Document requestMessage) throws DocumentedException;
}
