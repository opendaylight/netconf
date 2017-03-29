/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.testtool.xml.rpc;

import com.google.common.base.Preconditions;
import java.io.File;
import java.util.List;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "rpcs")
@XmlAccessorType(XmlAccessType.FIELD)
public class Rpcs {

    @XmlElement(name = "rpc")
    private List<Rpc> rpcList;

    public Rpcs() {
    }

    public Rpcs(final List<Rpc> rpcList) {
        this.rpcList = rpcList;
    }

    public List<Rpc> getRpcList() {
        return rpcList;
    }

    public void setRpcList(final List<Rpc> rpcList) {
        this.rpcList = rpcList;
    }

    public static Rpcs loadRpcs(final File rpcFile) {
        Preconditions.checkState(rpcFile.exists());
        final JAXBContext jc;
        try {
            jc = JAXBContext.newInstance(Rpcs.class);
            return (Rpcs) jc.createUnmarshaller().unmarshal(rpcFile);
        } catch (final JAXBException e) {
            throw new IllegalStateException("Rpc unmarshall error", e);
        }
    }

}