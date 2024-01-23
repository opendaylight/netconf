/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.api.messages;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class OverloadMessageTestThread implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(OverloadMessageTestThread.class);
    private static final NetconfMessage NETCONF_MESSAGE;
    private final CountDownLatch latch;
    private final ArrayList<Long> executionTimes;

    static {
        Document document;
        StringBuilder message = new StringBuilder();

        //create big message
        message.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<hello xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n    <capabilities>\n");
        for (int i = 0; i < 10000; i++) {
            message.append("        <capability>urn:ietf:params:netconf:base:1.1</capability>\n");
        }
        message.append("    </capabilities>\n</hello>");

        try {
            document = XmlUtil.readXmlToDocument(message.toString());
        } catch (SAXException | IOException e) {
            throw new RuntimeException(e);
        }

        NETCONF_MESSAGE = new NetconfMessage(document);
    }

    OverloadMessageTestThread(CountDownLatch latch, ArrayList<Long> executionTimes) {
        this.latch = latch;
        this.executionTimes = executionTimes;
    }


    @Override
    public void run() {
        for (int i = 0; i < 3; i++) {
            long startTime = System.nanoTime();
            NETCONF_MESSAGE.toString();
            long endTime = System.nanoTime();

            executionTimes.add((endTime - startTime) / 1_000_000);
        }
        latch.countDown();
    }
}
