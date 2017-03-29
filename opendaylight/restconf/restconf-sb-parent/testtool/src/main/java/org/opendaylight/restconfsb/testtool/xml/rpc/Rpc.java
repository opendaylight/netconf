/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.testtool.xml.rpc;

import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class Rpc {
    private String module;
    private String name;
    @XmlElement(name = "invocation")
    List<Invocation> invocations;

    public Rpc() {
    }

    public Rpc(String module, String name, List<Invocation> invocations) {
        this.module = module;
        this.name = name;
        this.invocations = invocations;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Invocation> getInvocations() {
        return invocations;
    }

    public void setInvocations(List<Invocation> invocations) {
        this.invocations = invocations;
    }
}
