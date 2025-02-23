/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api.testlib;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public abstract class AbstractInstanceIdentifierTest {
    protected static final QNameModule INSTANCE_IDENTIFIER_MODULE_QNAME =
        QNameModule.ofRevision("instance:identifier:module", "2014-01-17");

    protected static final QName CONT_QNAME = QName.create(INSTANCE_IDENTIFIER_MODULE_QNAME, "cont");
    protected static final QName CONT1_QNAME = QName.create(CONT_QNAME, "cont1");
    protected static final QName RESET_QNAME = QName.create(CONT_QNAME, "reset");
    protected static final QName DELAY_QNAME = QName.create(CONT_QNAME, "delay");

    protected static final QName CASE_LEAF1_QNAME = QName.create("choice:ns", "case-leaf1");
    protected static final QName CHOICE_CONT_QNAME = QName.create("choice:ns", "case-cont1");

    protected static final QName LIST_QNAME = QName.create("list:ns", "unkeyed-list");
    protected static final QName LIST_LEAF1_QNAME = QName.create("list:ns", "leaf1");
    protected static final QName LIST_LEAF2_QNAME = QName.create("list:ns", "leaf2");

    protected static final QName DATA_LEAF_QNAME = QName.create("map:ns", "data-leaf");
    protected static final QName KEY_LEAF_QNAME = QName.create("map:ns", "key-leaf");
    protected static final QName MAP_CONT_QNAME = QName.create("map:ns", "my-map");

    protected static final QName LEAF_SET_QNAME = QName.create("set:ns", "my-set");

    protected static final QName CONT_AUG_QNAME = QName.create("test-ns-aug", "container-aug");
    protected static final QName LEAF_AUG_QNAME = QName.create("test-ns-aug", "leaf-aug");

    protected static final QName PATCH_CONT_QNAME =
        QName.create("instance:identifier:patch:module", "2015-11-21", "patch-cont");
    protected static final QName MY_LIST1_QNAME = QName.create(PATCH_CONT_QNAME, "my-list1");
    protected static final QName LEAF_NAME_QNAME = QName.create(PATCH_CONT_QNAME, "name");
    protected static final QName MY_LEAF11_QNAME = QName.create(PATCH_CONT_QNAME, "my-leaf11");
    protected static final QName MY_LEAF12_QNAME = QName.create(PATCH_CONT_QNAME, "my-leaf12");

    public static final @NonNull EffectiveModelContext IID_SCHEMA =
        YangParserTestUtils.parseYangResources(AbstractInstanceIdentifierTest.class,
            "/instance-identifier/augment-augment-module.yang",
            "/instance-identifier/augment-iip-module.yang",
            "/instance-identifier/augment-module-leaf-list.yang",
            "/instance-identifier/augment-module.yang",
            "/instance-identifier/bar-module.yang",
            "/instance-identifier/choice-model.yang",
            "/instance-identifier/foo-module.yang",
            "/instance-identifier/instance-identifier-module.yang",
            "/instance-identifier/instance-identifier-patch-module.yang",
            "/instance-identifier/leaf-set-model.yang",
            "/instance-identifier/map-model.yang",
            "/instance-identifier/sal-remote.yang",
            "/instance-identifier/test-model-aug.yang",
            "/instance-identifier/test-model.yang",
            "/instance-identifier/unkeyed-list-model.yang");
    public static final @NonNull DatabindContext IID_DATABIND = DatabindContext.ofModel(IID_SCHEMA);

    protected static final InputStream stringInputStream(final String str) {
        return new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8));
    }
}
