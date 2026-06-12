/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api.testlib;

import java.text.ParseException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.api.query.FieldsParam;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public abstract class AbstractFieldsTranslatorTest extends AbstractJukeboxTest {
    protected static final QNameModule Q_NAME_MODULE_TEST_SERVICES =
        QNameModule.ofRevision("tests:test-services", "2019-03-25");
    protected static final QNameModule Q_NAME_MODULE_AUGMENTED_JUKEBOX =
        QNameModule.ofRevision("http://example.com/ns/augmented-jukebox", "2016-05-05");
    protected static final QNameModule Q_NAME_MODULE_FOO = QNameModule.ofRevision("urn:foo", "2023-03-27");

    protected static final EffectiveModelContext TEST_SERVICES_SCHEMA =
        YangParserTestUtils.parseYangResources(AbstractFieldsTranslatorTest.class,
            "/test-services/test-services@2019-03-25.yang");
    protected static final EffectiveModelContext FOO_SCHEMA =
        YangParserTestUtils.parseYangResources(AbstractFieldsTranslatorTest.class,
            "/same-qname-nodes/foo.yang");
    private DataSchemaContext jukeboxSchemaNode;
    private DataSchemaContext testServices;
    private DataSchemaContext foo;

    // container augmented library
    protected static final QName AUGMENTED_LIBRARY_Q_NAME = QName.create(Q_NAME_MODULE_AUGMENTED_JUKEBOX,
        "augmented-library");

    // leaf speed
    protected static final QName SPEED_Q_NAME = QName.create(Q_NAME_MODULE_AUGMENTED_JUKEBOX, "speed");

    // container test data
    protected static final QName TEST_DATA_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "test-data");

    // list services
    protected static final QName SERVICES_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "services");

    // leaf type-of-service
    protected static final QName TYPE_OF_SERVICE_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "type-of-service");

    // list instance
    protected static final QName INSTANCE_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "instance");

    // leaf instance-name
    protected static final QName INSTANCE_NAME_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "instance-name");

    // leaf provider
    protected static final QName PROVIDER_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "provider");

    // container next-data
    protected static final QName NEXT_DATA_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "next-data");

    // leaf next-service
    protected static final QName NEXT_SERVICE_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "next-service");

    // leaf-list protocols
    protected static final QName PROTOCOLS_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "protocols");

    // container foo
    protected static final QName FOO_Q_NAME = QName.create(Q_NAME_MODULE_FOO, "foo");

    // container bar
    protected static final QName BAR_Q_NAME = QName.create(Q_NAME_MODULE_FOO, "bar");

    // container baz
    protected static final QName BAZ_Q_NAME = QName.create(Q_NAME_MODULE_FOO, "baz");

    // leaf alpha
    protected static final QName ALPHA_Q_NAME = QName.create(Q_NAME_MODULE_FOO, "alpha");

    // container beta
    protected static final QName BETA_Q_NAME = QName.create(Q_NAME_MODULE_FOO, "beta");

    // container foo
    protected static final QName GAMMA_Q_NAME = QName.create(Q_NAME_MODULE_FOO, "gamma");

    // container foo
    protected static final QName DELTA_Q_NAME = QName.create(Q_NAME_MODULE_FOO, "delta");

    // container foo
    protected static final QName EPSILON_Q_NAME = QName.create(Q_NAME_MODULE_FOO, "epsilon");

    protected DataSchemaContext jukeboxSchemaNode() {
        return jukeboxSchemaNode;
    }

    protected DataSchemaContext testServices() {
        return testServices;
    }

    protected DataSchemaContext foo() {
        return foo;
    }

    protected void initFieldsTranslatorTest() {
        jukeboxSchemaNode = DataSchemaContextTree.from(JUKEBOX_SCHEMA).getRoot().childByQName(JUKEBOX_QNAME);
        testServices = DataSchemaContextTree.from(TEST_SERVICES_SCHEMA).getRoot().childByQName(TEST_DATA_Q_NAME);
        foo = DataSchemaContextTree.from(FOO_SCHEMA).getRoot().childByQName(FOO_Q_NAME);
    }

    protected static @NonNull FieldsParam assertFields(final String input) {
        try {
            return FieldsParam.parse(input);
        } catch (ParseException e) {
            throw new AssertionError(e);
        }
    }
}