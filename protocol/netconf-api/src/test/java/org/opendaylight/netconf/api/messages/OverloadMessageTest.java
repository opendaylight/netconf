/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.api.messages;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
Not really a test, rather a tool to replicate issue for WIP phase.
Create multiple threads witch then calls NetconfMessage.toString().
Execution time of toString() method are stored for all threads in order in witch threads finished call.
 */
public class OverloadMessageTest {

    private static final Logger LOG = LoggerFactory.getLogger(OverloadMessageTest.class);

    @Test
    public void testMessage() throws InterruptedException {
        ArrayList<Long> executionTimes;

        for (int i = 0; i < 5; i++) {
            executionTimes = new ArrayList<>();
            runThreads(200, 0, 500000, executionTimes);
            LOG.info("Size: " + TransformerProvider.maxSize + " Max: " + Collections.max(executionTimes)
                + " Avg: " + average(executionTimes));
            LOG.info("Run Time: " + TransformerProvider.runTime / 1_000_000);
            TransformerProvider.runTime = 0;

            Thread.sleep(5000);
            runThreads(1, 0, 0, executionTimes);
            TransformerProvider.setMaxSize(1);
            assertEquals(1, TransformerProvider.size());
        }
        LOG.info("..............................");
        for (int i = 0; i < 5; i++) {
            executionTimes = new ArrayList<>();
            runThreads(100, 10, 0, executionTimes);
            LOG.info("Size: " + TransformerProvider.maxSize + " Max: " + Collections.max(executionTimes)
                + " Avg: " + average(executionTimes));
            LOG.info("Run Time: " + TransformerProvider.runTime / 1_000_000);
            TransformerProvider.runTime = 0;

            Thread.sleep(5000);
            runThreads(1, 0, 0, executionTimes);
            TransformerProvider.setMaxSize(1);
            assertEquals(1, TransformerProvider.size());
        }


    }

    private void runThreads(int threadsAmount, int delayMillis, int delayNanos, ArrayList<Long> executionTimes)
        throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(threadsAmount);
        ArrayList<Thread> threads = new ArrayList<>();

        //Create threads
        for (int i = 0; i < threadsAmount; i++) {
            threads.add(new Thread(new OverloadMessageTestThread(latch, executionTimes)));
        }
        //start threads
        for (Thread t : threads) {
            t.start();
            Thread.sleep(delayMillis, delayNanos);
        }

        //wait for all threads to finish
        latch.await(1, TimeUnit.MINUTES);
    }

    @Test
    public void multipleTest()
        throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1400);
        ArrayList<Thread> threads = new ArrayList<>();
        ArrayList<Long> executionTimes = new ArrayList<>();

        //Create threads
        for (int i = 0; i < 200; i++) {
            threads.add(new Thread(new OverloadMessageTestThread(latch, executionTimes)));
        }
        //start threads
        for (Thread t : threads) {
            t.start();
            Thread.sleep(1);
        }
        threads = new ArrayList<>();
        Thread.sleep(50);

        //Create threads
        for (int i = 0; i < 500; i++) {
            threads.add(new Thread(new OverloadMessageTestThread(latch, executionTimes)));
        }
        //start threads
        for (Thread t : threads) {
            t.start();
            Thread.sleep(5);
        }
        threads = new ArrayList<>();
        Thread.sleep(50);

        //Create threads
        for (int i = 0; i < 100; i++) {
            threads.add(new Thread(new OverloadMessageTestThread(latch, executionTimes)));
        }
        //start threads
        for (Thread t : threads) {
            t.start();
            Thread.sleep(50);
        }
        threads = new ArrayList<>();

        //Create threads
        for (int i = 0; i < 200; i++) {
            threads.add(new Thread(new OverloadMessageTestThread(latch, executionTimes)));
        }
        //start threads
        for (Thread t : threads) {
            t.start();
            Thread.sleep(0,500000);
        }
        threads = new ArrayList<>();
        Thread.sleep(50);

        //Create threads
        for (int i = 0; i < 400; i++) {
            threads.add(new Thread(new OverloadMessageTestThread(latch, executionTimes)));
        }
        //start threads
        for (Thread t : threads) {
            t.start();
            Thread.sleep(10);
        }

        //wait for all threads to finish
        latch.await(1, TimeUnit.MINUTES);

        LOG.info("Size: " + TransformerProvider.maxSize + " Max: " + Collections.max(executionTimes)
            + " Avg: " + average(executionTimes));
        LOG.info("Run Time: " + TransformerProvider.runTime / 1_000_000);
    }

    private void printTimes(ArrayList<Long> executionTimes) {
        for (long time : executionTimes) {
            LOG.info(String.valueOf(time));
        }
    }

    private long average(ArrayList<Long> executionTimes) {
        long sum = 0;
        for (long d : executionTimes) {
            sum += d;
        }
        return sum / executionTimes.size();
    }
}

