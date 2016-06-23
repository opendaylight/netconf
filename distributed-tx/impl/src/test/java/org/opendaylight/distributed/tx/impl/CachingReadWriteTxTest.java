/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.impl;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.distributed.tx.api.DTxException;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import static org.junit.Assert.fail;

public class CachingReadWriteTxTest {
    DTXTestTransaction testTx;

    InstanceIdentifier<DTXTestTransaction.myDataObj> dataobjIid = InstanceIdentifier.create(DTXTestTransaction.myDataObj.class);
    @Before
    public void testInit(){
        this.testTx = new DTXTestTransaction();
        testTx.addInstanceIdentifiers(dataobjIid);
    }

    /**
     * Test constructor
     */
    @Test
    public void testConstructor() {
       new CachingReadWriteTx(testTx);
    }

    /**
     * Test successful read()
     */
    @Test
    public void testReadWithObjEx()
    {
        testTx.createObjForIdentifier(dataobjIid);
        CachingReadWriteTx cacheRWTx = new CachingReadWriteTx(testTx);
        Optional<DTXTestTransaction.myDataObj> readData = Optional.absent();

        CheckedFuture<Optional<DTXTestTransaction.myDataObj>, ReadFailedException> readResult = cacheRWTx.read(LogicalDatastoreType.OPERATIONAL, dataobjIid);
        try{
            readData = readResult.checkedGet();
        }catch (Exception e)
        {
            fail("Get unexpected exception from read()");
        }

        Assert.assertTrue("Can't read from the transaction", readData.isPresent());
    }

    /**
     * Test read() with exception
     */
    @Test
    public void testReadFailWithExistingObj()
    {
        testTx.createObjForIdentifier(dataobjIid);
        CachingReadWriteTx cacheRWTx = new CachingReadWriteTx(testTx);
        testTx.setReadExceptionByIid(dataobjIid, true);

        CheckedFuture<Optional<DTXTestTransaction.myDataObj>, ReadFailedException> readResult = cacheRWTx.read(LogicalDatastoreType.OPERATIONAL, dataobjIid);
        try{
            readResult.checkedGet();
            fail("Can't get exception from read ()");
        }catch (Exception e)
        {
            Assert.assertTrue("Can't get ReadFailedException from read()", e instanceof ReadFailedException);
        }
    }

    /**
     * Test asyncPut() with successful tx provider put().
     */
    @Test
    public void testAsyncPutWithNonExistingObj() {
        CachingReadWriteTx cacheRWTx = new CachingReadWriteTx(testTx);
        int numberOfObjs = (int)(Math.random() * 10) + 1;
        int expectedDataSizeInTx = 1;

        for(int i = 0; i < numberOfObjs; i++){
            CheckedFuture<Void, DTxException> cf = cacheRWTx.asyncPut(LogicalDatastoreType.OPERATIONAL, dataobjIid, new DTXTestTransaction.myDataObj());
            try{
                cf.checkedGet();
            }catch (Exception e)
            {
                fail("Get unexpected exception from asyncPut()");
            }
        }

        Assert.assertEquals("Cache size is wrong", cacheRWTx.getSizeOfCache(), numberOfObjs);
        Assert.assertEquals("Data size is wrong", expectedDataSizeInTx, testTx.getTxDataSizeByIid(dataobjIid));
    }

    /**
     * Test asyncPut() with failed tx provider read()
     */
    @Test
    public void testAsyncPutWithNonExistingObjReadFail() {
        int numberOfObjs = (int)(Math.random() * 10) + 1;
        int expectedDataSizeInTx = 0, expectedCacheDataSize = 0;
        CachingReadWriteTx cacheRWTx = new CachingReadWriteTx(testTx);
        for (int i = 0; i < numberOfObjs; i++ ) {
            testTx.setReadExceptionByIid(dataobjIid,true);
            CheckedFuture<Void, DTxException> cf = cacheRWTx.asyncPut(LogicalDatastoreType.OPERATIONAL, dataobjIid, new DTXTestTransaction.myDataObj());
            try {
                cf.checkedGet();
                fail("Can't get exception from asyncPut()");
            } catch (Exception e) {
                Assert.assertTrue("Can't get EditFailedException from asyncPut()", e instanceof DTxException.ReadFailedException);
            }
        }

        Assert.assertEquals("Cache size is wrong", expectedCacheDataSize, cacheRWTx.getSizeOfCache() );
        Assert.assertEquals("Data size is wrong", expectedDataSizeInTx, testTx.getTxDataSizeByIid(dataobjIid));
    }

    /**
     * Test asyncPut() with failed tx provider put()
     */
    @Test
    public void testAsyncPutWithNonExistingObjPutFail() {
        int numberOfObjs = (int)(Math.random() * 10) + 1;
        int expectedDataSizeInTx = 0;
        CachingReadWriteTx cacheRWTx = new CachingReadWriteTx(testTx);

        for (int i = 0; i < numberOfObjs; i++ ) {
            testTx.setPutExceptionByIid(dataobjIid,true);
            CheckedFuture<Void, DTxException> cf = cacheRWTx.asyncPut(LogicalDatastoreType.OPERATIONAL, dataobjIid, new DTXTestTransaction.myDataObj());
            try {
                cf.checkedGet();
                fail("Can't get exception from asyncPut()");
            } catch (Exception e) {
                Assert.assertTrue("Can't get RuntimeException from asyncPut()", e instanceof DTxException);
            }
        }

        Assert.assertEquals("Cache size is wrong", numberOfObjs, cacheRWTx.getSizeOfCache());
        Assert.assertEquals("Data size is wrong", expectedDataSizeInTx, testTx.getTxDataSizeByIid(dataobjIid));
    }

    /**
     * Test asyncMerge() with successful tx provider merge()
     */
    @Test
    public void testAsyncMergeWithNonExistingObj() {
        CachingReadWriteTx cacheRWTx = new CachingReadWriteTx(testTx);
        int numberOfObjs = (int)(Math.random() * 10) + 1;
        int expectedDataSizeInTx = 1;
        for(int i = 0; i < numberOfObjs; i++){
            CheckedFuture<Void, DTxException> cf =  cacheRWTx.asyncMerge(LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(DTXTestTransaction.myDataObj.class), new DTXTestTransaction.myDataObj());
            try{
                cf.checkedGet();
            }catch (Exception e)
            {
                fail("Get unexpected exception from asyncMerge()");
            }
        }

        Assert.assertEquals("Cache size is wrong", numberOfObjs, cacheRWTx.getSizeOfCache());
        Assert.assertEquals("Data size is wrong", expectedDataSizeInTx, testTx.getTxDataSizeByIid(dataobjIid));
    }

    /**
     * Test asyncMerge() with failed tx provider read()
     */
    @Test
    public void testAsyncMergeWithNonExistingObjReadFail() {
        CachingReadWriteTx cacheRWTx = new CachingReadWriteTx(testTx);
        int numberOfObjs = (int)(Math.random() * 10) + 1;
        int expectedDataSizeInTx = 0, expectedCacheDataSize = 0;

        for(int i = 0; i < numberOfObjs; i++)
        {
            testTx.setReadExceptionByIid(dataobjIid,true);
            CheckedFuture<Void, DTxException> cf = cacheRWTx.asyncMerge(LogicalDatastoreType.OPERATIONAL, dataobjIid, new DTXTestTransaction.myDataObj());
            try
            {
                cf.checkedGet();
                fail("Can't get the exception from asyncMerge()");
            }catch(Exception e)
            {
                Assert.assertTrue("Can't get ReadFailedException from asyncMerge()", e instanceof DTxException.ReadFailedException);
            }
        }

        Assert.assertEquals("Cache size is wrong", expectedCacheDataSize, cacheRWTx.getSizeOfCache());
        Assert.assertEquals("Data size is wrong", expectedDataSizeInTx, testTx.getTxDataSizeByIid(dataobjIid) );
    }

    /**
     * Test asyncMerge() with failed tx provider merge()
     */
    @Test
    public void testAsyncMergeWithNonExistingObjMergeFail() {
        CachingReadWriteTx cacheRWTx = new CachingReadWriteTx(testTx);
        int numberOfObjs = (int)(Math.random() * 10) + 1;
        int expectedDataSizeInTx = 0;
        for(int i = 0; i < numberOfObjs; i++)
        {
            testTx.setMergeExceptionByIid(dataobjIid,true);
            CheckedFuture<Void, DTxException> cf = cacheRWTx.asyncMerge(LogicalDatastoreType.OPERATIONAL, dataobjIid, new DTXTestTransaction.myDataObj());
            try
            {
                cf.checkedGet();
                fail("Can't get the exception from asyncMerge()");
            }catch(Exception e)
            {
                Assert.assertTrue("Can't get DTxException from asyncMerge()", e instanceof DTxException);
            }
        }

        Assert.assertEquals("Cache size is wrong", numberOfObjs, cacheRWTx.getSizeOfCache());
        Assert.assertEquals("Data size is wrong", expectedDataSizeInTx, testTx.getTxDataSizeByIid(dataobjIid) );
    }

    /**
     * Test asyncDelete() with successfully tx provider delete()
     */
    @Test
    public void testAsyncDeleteWithExistingObj() {
        int expectedDataSizeInTx = 0, expectedCacheDataSize = 1;
        CachingReadWriteTx cacheRWTx = new CachingReadWriteTx(testTx);
        testTx.createObjForIdentifier(dataobjIid);
        CheckedFuture<Void, DTxException> f = cacheRWTx.asyncDelete(LogicalDatastoreType.OPERATIONAL, dataobjIid);
        try
        {
           f.checkedGet();
        }catch (Exception e)
        {
            fail("Get unexpected exception from asyncDelete()");
        }

        Assert.assertEquals("Cache size is wrong", expectedCacheDataSize, cacheRWTx.getSizeOfCache());
        Assert.assertEquals("Data size is wrong", expectedDataSizeInTx, testTx.getTxDataSizeByIid(dataobjIid));
    }

    /**
     * Test asyncDelete() with failed tx provider read()
     */
    @Test
    public void testAsyncDeleteWithExistingObjReadFail() {
        int expectedDataSizeInTx = 1, expectedCacheDataSize = 0;
        CachingReadWriteTx cacheRWTx = new CachingReadWriteTx(testTx);
        testTx.createObjForIdentifier(dataobjIid);
        testTx.setReadExceptionByIid(dataobjIid,true);

        CheckedFuture<Void, DTxException> f = cacheRWTx.asyncDelete(LogicalDatastoreType.OPERATIONAL, dataobjIid);
        try{
                f.checkedGet();
                fail("Can't get the exception from asyncDelete()");
        }catch (Exception e)
        {
                Assert.assertTrue("Can't get the EditFailedException from asyncDelete()", e instanceof DTxException.ReadFailedException);
        }

        Assert.assertEquals("Cache size is wrong", expectedCacheDataSize, cacheRWTx.getSizeOfCache());
        Assert.assertEquals("Data size is wrong", expectedDataSizeInTx, testTx.getTxDataSizeByIid(dataobjIid));
    }

    /**
     * Test asyncDelete() with failed tx provider delete()
     */
    @Test
    public void testAsyncDeleteWithExistingObjDeleteFail() {
        int expectedDataSizeInTx = 1, expectedCacheDataSize = 1;
        CachingReadWriteTx cacheRWTx = new CachingReadWriteTx(testTx);
        testTx.createObjForIdentifier(dataobjIid);
        testTx.setDeleteExceptionByIid(dataobjIid,true);
        CheckedFuture<Void, DTxException> f = cacheRWTx.asyncDelete(LogicalDatastoreType.OPERATIONAL, dataobjIid);
        try
        {
            f.checkedGet();
            fail("Can't get the exception from asyncDelete()");
        }catch (Exception e)
        {
            Assert.assertTrue("Can't get the RuntimeException from asyncDelete()", e instanceof DTxException);
        }
        Assert.assertEquals("Cache size is wrong", expectedCacheDataSize, cacheRWTx.getSizeOfCache());
        Assert.assertEquals("Data size is wrong", expectedDataSizeInTx, testTx.getTxDataSizeByIid(dataobjIid));
    }

    /**
     * Test submit() with successful tx provider submit()
     */
    @Test
    public void testSubmitSucceed(){
        CachingReadWriteTx cacheRWTx = new CachingReadWriteTx(testTx);
        CheckedFuture<Void, TransactionCommitFailedException> cf = cacheRWTx.submit();
        try {
            cf.checkedGet();
        } catch (Exception e) {
            fail("Get unexpected exception from submit()");
        }
    }

    /**
     * Test submit() with failed tx provider submit()
     */
    @Test
    public void testSubmitFail() {
        CachingReadWriteTx cacheRWTx = new CachingReadWriteTx(testTx);
        testTx.setSubmitException(true);

        CheckedFuture<Void, TransactionCommitFailedException> cf = cacheRWTx.submit();
        try
        {
            cf.checkedGet();
            fail("Can't get the exception from submit()");
        }catch(Exception e)
        {
            Assert.assertTrue("Can't get the TransactionCommitFailException from submit()", e instanceof TransactionCommitFailedException);
        }
    }
}
