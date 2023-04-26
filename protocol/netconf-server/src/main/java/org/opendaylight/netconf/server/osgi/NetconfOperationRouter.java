/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.osgi;

import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.server.NetconfServerSession;
import org.w3c.dom.Document;

public interface NetconfOperationRouter {

    Document onNetconfMessage(Document message, NetconfServerSession session) throws DocumentedException;
}
