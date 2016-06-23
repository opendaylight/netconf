/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.impl;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.TransactionStatus;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yangtools.yang.binding.DataContainer;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class DTXTestTransaction implements ReadWriteTransaction {
    Map<InstanceIdentifier<?>, Boolean> readExceptionMap = new ConcurrentHashMap<>();
    Map<InstanceIdentifier<?>, Boolean> putExceptionMap = new ConcurrentHashMap<>();
    Map<InstanceIdentifier<?>, Boolean> mergeExceptionMap = new ConcurrentHashMap<>();
    Map<InstanceIdentifier<?>, Boolean> deleteExceptionMap = new ConcurrentHashMap<>();
    boolean  submitException = false;
    static int delayTime = 20;

    private Map<InstanceIdentifier<?>,ConcurrentLinkedDeque<DataObject>> txDataMap = new ConcurrentHashMap<>();

    public void setReadExceptionByIid(InstanceIdentifier<?> instanceIdentifier, boolean exception){
        this.readExceptionMap.put(instanceIdentifier, exception);
    }
    public void setPutExceptionByIid(InstanceIdentifier<?> instanceIdentifier, boolean exception)
    {
        this.putExceptionMap.put(instanceIdentifier, exception);
    }
    public void setMergeExceptionByIid(InstanceIdentifier<?> instanceIdentifier, boolean exception)
    {
        this.mergeExceptionMap.put(instanceIdentifier, exception);
    }
    public void setDeleteExceptionByIid(InstanceIdentifier<?> instanceIdentifier, boolean exception)
    {
        this.deleteExceptionMap.put(instanceIdentifier, exception);
    }
    public void setSubmitException( boolean exception)
    {
        this.submitException = exception;
    }

    public void addInstanceIdentifiers(InstanceIdentifier<?>... iids){
        for (InstanceIdentifier<?> iid : iids) {
            this.readExceptionMap.put(iid, false);
            this.deleteExceptionMap.put(iid, false);
            this.putExceptionMap.put(iid, false);
            this.mergeExceptionMap.put(iid, false);
            this.txDataMap.put(iid, new ConcurrentLinkedDeque<DataObject>());
        }
    }

    /**
     * Get the size of data with the specific data IID
     * @param instanceIdentifier the specific data IID
     * @return size of data
     */
    public int getTxDataSizeByIid(InstanceIdentifier<?> instanceIdentifier) {
        return this.txDataMap.get(instanceIdentifier).size();
    }

    /**
     * Create the data object with the specific data IID
     * @param instanceIdentifier the specific IID
     */
    public void createObjForIdentifier(InstanceIdentifier<?> instanceIdentifier)
    {
        txDataMap.get(instanceIdentifier).add(new myDataObj());
    }

    @Override
    public <T extends DataObject> CheckedFuture<Optional<T>, ReadFailedException> read(LogicalDatastoreType logicalDatastoreType, final InstanceIdentifier<T> instanceIdentifier) {
        T obj = null;

        if(txDataMap.get(instanceIdentifier).size() > 0)
            obj = (T)txDataMap.get(instanceIdentifier).getFirst();

        final Optional<T> retOpt = Optional.fromNullable(obj);

        final SettableFuture<Optional<T>> retFuture = SettableFuture.create();
        Runnable readResult = new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(delayTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                boolean readException = getAndResetExceptionWithInstanceIdentifier(readExceptionMap, instanceIdentifier);
                if (readException == false){
                    retFuture.set(retOpt);
                }else {
                    retFuture.setException(new Throwable("Read error"));
                }
                retFuture.notifyAll();
            }
        };

        new Thread(readResult).start();

        Function<Exception, ReadFailedException> f = new Function<Exception, ReadFailedException>() {
            @Nullable
            @Override
            public ReadFailedException apply(@Nullable Exception e) {
                return new ReadFailedException("Read failed", e);
            }
        };

        return Futures.makeChecked(retFuture, f);
    }

    @Override
    public <T extends DataObject> void put(LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<T> instanceIdentifier, T t) {
        boolean putException = getAndResetExceptionWithInstanceIdentifier(putExceptionMap, instanceIdentifier);
        if(putException == false) {
               txDataMap.get(instanceIdentifier).clear();
               txDataMap.get(instanceIdentifier).add(t);
        } else {
           throw new RuntimeException("Put exception");
       }
    }

    @Deprecated
    @Override
    public <T extends DataObject> void put(LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<T> instanceIdentifier, T t, boolean b) {
        throw new UnsupportedOperationException("Unimplemented");
    }

    @Override
    public <T extends DataObject> void merge(LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<T> instanceIdentifier, T t) {
        boolean exception = getAndResetExceptionWithInstanceIdentifier(mergeExceptionMap, instanceIdentifier);
        if(exception == false) {
                txDataMap.get(instanceIdentifier).clear();
                txDataMap.get(instanceIdentifier).add(t);
        }
        else {
            throw new RuntimeException("Merge exception");
        }
    }

    @Deprecated
    @Override
    public <T extends DataObject> void merge(LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<T> instanceIdentifier, T t, boolean b) {
        throw new UnsupportedOperationException("Unimplemented");
    }

    @Override
    public boolean cancel() {
        return false;
    }

    @Override
    public void delete(LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<?> instanceIdentifier) {
        boolean deleteException = getAndResetExceptionWithInstanceIdentifier(deleteExceptionMap, instanceIdentifier);
        if(deleteException == false) {
            if (txDataMap.get(instanceIdentifier).size() > 0)
                txDataMap.get(instanceIdentifier).clear();
        }
        else {
            throw new RuntimeException("Delete exception");
        }
    }

    @Override
    public CheckedFuture<Void, TransactionCommitFailedException> submit() {
        final SettableFuture<Void> retFuture = SettableFuture.create();

        final Runnable submitResult = new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(delayTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (!submitException){
                  retFuture.set(null);
                }else {
                    setSubmitException(false);
                    retFuture.setException(new RuntimeException("Submit error"));
                }
                retFuture.notifyAll();
            }
        };

        new Thread(submitResult).start();

        Function<Exception, TransactionCommitFailedException> f = new Function<Exception, TransactionCommitFailedException>() {
            @Nullable
            @Override
            public TransactionCommitFailedException apply(@Nullable Exception e) {
                return new TransactionCommitFailedException("Submit failed", e);
            }
        };

        return Futures.makeChecked(retFuture, f);
    }

    @Override
    public ListenableFuture<RpcResult<TransactionStatus>> commit() {
        return null;
    }

    public boolean getAndResetExceptionWithInstanceIdentifier(Map<InstanceIdentifier<?>, Boolean> exceptionMap, InstanceIdentifier<?> iid){
        synchronized (this){
            boolean exception = exceptionMap.get(iid);
            exceptionMap.put(iid, false);
            return exception;
        }
    }

    @Override
    public Object getIdentifier() {
        return null;
    }

    public static class myDataObj implements DataObject{
        @Override
        public Class<? extends DataContainer> getImplementedInterface() {
            return myDataObj.class;
        }
    }
}
