/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.testtool.core.impl.rpc;

import org.opendaylight.controller.config.util.xml.XmlElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import java.util.Optional;

public class RpcMappingDefaultImpl implements RpcMapping {

    private static final Logger LOG = LoggerFactory.getLogger(RpcMappingDefaultImpl.class);

    @Override
    public Optional<Document> getResponse(XmlElement rpcElement) {
        LOG.info("getResponse");
        return null;
    }

}
