/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.restconf.impl.test.incubate;

import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.opendaylight.infrautils.inject.guice.testutils.AnnotationsModule;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.mdsal.binding.api.DataBroker;

/**
 * Test for {@link InMemoryMdsalModule}.
 *
 * <p>This will be removed when the local {@link InMemoryMdsalModule} incubating here
 * in netconf will be replaced by the one from mdsal.
 *
 * @author Michael Vorburger.ch
 */
public class InMemoryMdsalModuleTest {

    public @Rule GuiceRule guice = new GuiceRule(InMemoryMdsalModule.class, AnnotationsModule.class);

    @Inject DataBroker dataBroker;

    @Test public void testDataBroker() throws InterruptedException, ExecutionException {
        dataBroker.newReadWriteTransaction().commit().get();
    }
}
