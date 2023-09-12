/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.rpc;

import java.util.List;
import org.opendaylight.netconf.api.xml.XmlElement;

public class DataList {
    private List<XmlElement> configList = List.of();

    public List<XmlElement> getConfigList() {
        return configList;
    }

    public void setConfigList(final List<XmlElement> configList) {
        this.configList = configList;
    }

    public void resetConfigList() {
        configList = List.of();
    }
}
