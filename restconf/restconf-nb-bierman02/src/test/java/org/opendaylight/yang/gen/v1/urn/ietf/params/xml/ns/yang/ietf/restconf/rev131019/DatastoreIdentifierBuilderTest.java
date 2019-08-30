/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev131019;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class DatastoreIdentifierBuilderTest {

    @Test(expected = IllegalArgumentException.class)
    public void testDatastoreIdentifierBuilder() {
        final DatastoreIdentifierBuilder datastoreIdentifierBuilder = new DatastoreIdentifierBuilder();
        assertNotNull(datastoreIdentifierBuilder);
        DatastoreIdentifierBuilder.getDefaultInstance("");
    }
}