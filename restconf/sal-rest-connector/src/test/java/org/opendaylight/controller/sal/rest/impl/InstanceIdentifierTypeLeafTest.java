/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.rest.impl;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class InstanceIdentifierTypeLeafTest {

    @Test
    public void test() throws Exception {
        final SchemaContext schemaContext = TestRestconfUtils.loadSchemaContext("/instanceidentifier");
        ControllerContext.getInstance().setGlobalSchema(schemaContext);
        final InstanceIdentifierContext<?> instanceIdentifier =
                ControllerContext.getInstance().toInstanceIdentifier(
                        "/iid-value-module:cont-iid/iid-list/iid-value-module%3Acont-iid");
        final YangInstanceIdentifier yiD = instanceIdentifier.getInstanceIdentifier();
        Assert.assertNotNull(yiD);
        final NodeIdentifierWithPredicates nodeId = (NodeIdentifierWithPredicates) yiD.getPathArguments().get(2);
        final Map<QName, Object> values = nodeId.getKeyValues();
        final Collection<Object> collection = values.values();
        final Object value = collection.iterator().next();
        Assert.assertTrue(value instanceof InstanceIdentifierContext<?>);
        final InstanceIdentifierContext<?> iidValue = (InstanceIdentifierContext<?>) value;
        final YangInstanceIdentifier yiDvalue = iidValue.getInstanceIdentifier();
        Assert.assertNotNull(yiDvalue);
        Assert.assertTrue(yiDvalue.contains(YangInstanceIdentifier
                .of(QName.create("iid:value:module", "cont-iid",
                        new SimpleDateFormat("yyyy-MM-dd").parse("2016-09-12")))));
    }

}
