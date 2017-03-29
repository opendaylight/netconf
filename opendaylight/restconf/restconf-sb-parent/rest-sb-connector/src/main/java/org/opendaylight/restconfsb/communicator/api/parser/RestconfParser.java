/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.api.parser;

import java.io.InputStream;
import java.util.List;
import org.opendaylight.restconfsb.communicator.api.stream.Stream;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module;

/**
 * Parses data defined by ietf-restconf yang module
 */
public interface RestconfParser {

    /**
     * Parses http reply which conforms to modules container defined by ietf-restconf to list of Module
     * @param stream http reply stream
     * @return modules
     */
    List<Module> parseModules(InputStream stream);

    /**
     * Parses http reply which conforms to streams container defined by ietf-restconf to list of Stream
     * @param stream http reply stream
     * @return streams
     */
    List<Stream> parseStreams(InputStream stream);
}