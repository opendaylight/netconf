/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.rpchandler;

import java.util.Optional;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.w3c.dom.Document;

public interface RpcHandler {

    Optional<Document> getResponse(XmlElement rpcElement);

}
