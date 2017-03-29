/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint.sal;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public class ReadWriteTxTest {

    @Mock
    private ReadOnlyTx readTx;
    @Mock
    private WriteOnlyTx writeTx;
    private TestData data;

    private ReadWriteTx tx;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        data = new TestData();
        tx = new ReadWriteTx(readTx, writeTx);
        doReturn(Futures.immediateCheckedFuture(true)).when(readTx).exists(data.datastore, data.path);
        doReturn(Futures.immediateCheckedFuture(Optional.of(data.data))).when(readTx).read(data.datastore, data.path);
        doNothing().when(writeTx).put(data.datastore, data.path, data.data);
        doNothing().when(writeTx).merge(data.datastore, data.path, data.data);
        doNothing().when(writeTx).delete(data.datastore, data.path);
        doReturn(Futures.immediateCheckedFuture(null)).when(writeTx).submit();
        doReturn(false).when(writeTx).cancel();
    }

    @Test
    public void testExists() throws Exception {
        final CheckedFuture<Boolean, ReadFailedException> result = tx.exists(data.datastore, data.path);
        Assert.assertEquals(readTx.exists(data.datastore, data.path), result);
    }

    @Test
    public void testRead() throws Exception {
        final CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> result = tx.read(data.datastore, data.path);
        Assert.assertEquals(readTx.read(data.datastore, data.path), result);
    }

    @Test
    public void testPut() throws Exception {
        tx.put(data.datastore, data.path, data.data);
        verify(writeTx).put(data.datastore, data.path, data.data);
    }

    @Test
    public void testMerge() throws Exception {
        tx.merge(data.datastore, data.path, data.data);
        verify(writeTx).merge(data.datastore, data.path, data.data);
    }

    @Test
    public void testDelete() throws Exception {
        tx.delete(data.datastore, data.path);
        verify(writeTx).delete(data.datastore, data.path);
    }

    @Test
    public void testSubmit() throws Exception {
        tx.submit();
        verify(writeTx).submit();
    }

    @Test
    public void testCancel() throws Exception {
        tx.cancel();
        verify(writeTx).cancel();
    }

}