/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.it.provider.datawriter;

import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.distributed.tx.it.model.rev150105.BenchmarkTestInput;

public abstract class AbstractDataWriter {
    long startTime, endTime;
    BenchmarkTestInput input;
    int txSucceed = 0, txError = 0;

    public AbstractDataWriter(BenchmarkTestInput input)
    {
        this.input = input;
    }

    /*
     * Writing data into the data store or the netconf devices
     */
    public abstract void writeData();

    /**
     * Get the number of successful transactions
     */
    public int getTxSucceed(){
        return txSucceed;
    }

    /**
     * Get executing time
     */
    public long getExecTime()
    {
        return ((endTime - startTime)/1000);
    }
}
