/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool.customrpc;

import java.util.List;
import javax.xml.bind.annotation.XmlElement;

class Rpc {

    @XmlElement(name = "input")
    private XmlData input;

    @XmlElement(name = "output")
    private List<XmlData> output;

    XmlData getInput() {
        return input;
    }

    List<XmlData> getOutput() {
        return output;
    }
}
