/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.api.messages;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class TransformerProvider {
    private static final Logger LOG = LoggerFactory.getLogger(TransformerProvider.class);
    private static final ArrayList<TransformerObject> TRANSFORMERS = new ArrayList<>();
    private static final int MAX_TRANSFORMERS_COUNT = 20; //defines how many transformers can exist simultaneously
    private static final int MAX_WAIT_TIME = 1000;  // wait time after witch task will try to create new transformer
    private static final int MAX_IDLE_TIME = 5000;  // time after witch idle transformer can be removed
    private static final int TRANSFORMER_CREATION_TIMEOUT = 250;
    // timeout between creation of transformers (can be overcome, if wait time is significantly long)
    private static final int MIN_CLEAR_PERIOD = 1000; // minimal time between cleaning of idle transformers
    private static final Object LOCK = new Object();
    private static long lastCreatedTime;
    private static long lastClearedTime;

    static long runTime = 0;//just for testing phase
    static int maxSize = 1; //just for testing phase

    static {
        TransformerObject transformerObject = createTransformer();
        transformerObject.setAvailable(true);
        TRANSFORMERS.add(transformerObject);
    }


    public void transform(Source source, Result result) throws TransformerException {
        long startTime = System.currentTimeMillis();

        long time;
        TransformerObject transformerObject = chooseTransformer();
        while (transformerObject == null) {
            time = System.currentTimeMillis();
            if ((time - startTime > MAX_WAIT_TIME && time > lastCreatedTime + TRANSFORMER_CREATION_TIMEOUT)
                || time - startTime > 3 * MAX_WAIT_TIME) {
                synchronized (TRANSFORMERS) {
                    if (TRANSFORMERS.size() < MAX_TRANSFORMERS_COUNT) {
                        transformerObject = createTransformer();
                        TRANSFORMERS.add(transformerObject);
                        setLastCreatedTime(time);

                        //just for testing phase
                        if (TRANSFORMERS.size() > maxSize) {
                            setMaxSize(TRANSFORMERS.size());
                        }

                        break;
                    }
                }
            }
            transformerObject = chooseTransformer();
        }

        long start = 0; //just for testing phase
        long end = 0;   //just for testing phase

        try {
            start = System.nanoTime();
            transformerObject.getTransformer().transform(source, result);
            end = System.nanoTime();
        } catch (TransformerException e) {
            throw new TransformerException(e);
        } finally {
            synchronized (TRANSFORMERS) {
                transformerObject.setAvailable(true);
            }
            clearTransformers();

            addTime(end - start); //just for testing phase
//            LOG.info(String.valueOf((end - start) / 1_000_000));
        }
    }

    private static void setLastCreatedTime(long time) {
        lastCreatedTime = time;
    }

    private static void clearTransformers() {
        synchronized (LOCK) {
            long time = System.currentTimeMillis();
            if (time > lastClearedTime + MIN_CLEAR_PERIOD) {
                synchronized (TRANSFORMERS) {
                    TRANSFORMERS.removeIf(transformerObj ->
                        (transformerObj.isAvailable() && time > transformerObj.getLastUsed() + MAX_IDLE_TIME));
                }
                lastClearedTime = time;
            }
        }
    }

    private static TransformerObject chooseTransformer() {
        synchronized (TRANSFORMERS) {
            for (TransformerObject transformerObj : TRANSFORMERS) {
                if (transformerObj.isAvailable()) {
                    transformerObj.setAvailable(false);
                    return transformerObj;
                }
            }
        }
        return null;
    }

    private static TransformerObject createTransformer() {
        final Transformer t;
        try {
            t = TransformerFactory.newInstance().newTransformer();
        } catch (TransformerConfigurationException | TransformerFactoryConfigurationError e) {
            throw new ExceptionInInitializerError(e);
        }
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

        return new TransformerObject(t);
    }


    /**
     * Just for testing phase.
     **/

    @VisibleForTesting
    public static int size() {
        return TRANSFORMERS.size();
    }

    @VisibleForTesting
    public static void setMaxSize(int size) {
        maxSize = size;
    }

    public static void addTime(long time) {
        runTime += time;
    }

}
