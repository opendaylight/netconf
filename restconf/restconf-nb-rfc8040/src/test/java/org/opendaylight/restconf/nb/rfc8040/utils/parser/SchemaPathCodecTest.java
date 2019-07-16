/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class SchemaPathCodecTest {

    private static final QNameModule MODULE_LIST_TEST = QNameModule.create(
            URI.create("list:test"), Revision.of("2016-04-29"));
    private static final QNameModule MODULE_SERIALIZER_TEST_INCLUDED = QNameModule.create(
            URI.create("serializer:test:included"), Revision.of("2016-06-06"));
    private static final QNameModule MODULE_SERIALIZER_TEST = QNameModule.create(
            URI.create("serializer:test"), Revision.of("2016-06-06"));

    private static SchemaContext schemaContext;

    @BeforeClass
    public static void setupSchemaContext() {
        try {
            final Collection<File> yangFiles1 = TestRestconfUtils.loadFiles("/restconf/parser");
            final Collection<File> yangFiles2 = TestRestconfUtils.loadFiles("/restconf/parser/serializer");
            final List<File> allToBeLoadedYangFiles = new ArrayList<>(yangFiles1);
            allToBeLoadedYangFiles.addAll(yangFiles2);
            schemaContext = YangParserTestUtils.parseYangFiles(allToBeLoadedYangFiles);
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Cannot prepare testing schema context", e);
        }
    }

    @Test
    public void testSerializationOfTheRootPath() {
        final String serializedPath = SchemaPathCodec.serialize(SchemaPath.ROOT, schemaContext);
        Assert.assertEquals("/", serializedPath);
    }

    @Test
    public void testSerializationOfTheSamePath() {
        final String serializedPath = SchemaPathCodec.serialize(SchemaPath.SAME, schemaContext);
        Assert.assertEquals(".", serializedPath);
    }

    @Test
    public void testDeserializationOfTheRootPath() {
        Assert.assertEquals(SchemaPath.ROOT, SchemaPathCodec.deserialize("/", schemaContext));
    }

    @Test
    public void testDeserializationOfTheSamePath() {
        Assert.assertEquals(SchemaPath.SAME, SchemaPathCodec.deserialize(".", schemaContext));
    }

    @Test
    public void testSerializationOfAbsPathWithOneModule() {
        final SchemaPath schemaPath = SchemaPath.create(true,
                QName.create(MODULE_LIST_TEST, "top"),
                QName.create(MODULE_LIST_TEST, "list1"),
                QName.create(MODULE_LIST_TEST, "list2"),
                QName.create(MODULE_LIST_TEST, "result"));
        final String serializedPath = SchemaPathCodec.serialize(schemaPath, schemaContext);
        Assert.assertEquals("/list-test:top/list1/list2/result", serializedPath);
    }

    @Test
    public void testDeserializationOfAbsPathWithOneModule() {
        final SchemaPath expectedSchemaPath = SchemaPath.create(true,
                QName.create(MODULE_LIST_TEST, "top"),
                QName.create(MODULE_LIST_TEST, "list1"),
                QName.create(MODULE_LIST_TEST, "list2"));
        final SchemaPath deserializedPath = SchemaPathCodec.deserialize(
                "/list-test:top/list1/list2", schemaContext);
        Assert.assertEquals(expectedSchemaPath, deserializedPath);
    }

    @Test
    public void testSerializationOfRelPathWithOneModule() {
        final SchemaPath schemaPath = SchemaPath.create(false,
                QName.create(MODULE_LIST_TEST, "list1"),
                QName.create(MODULE_LIST_TEST, "list2"),
                QName.create(MODULE_LIST_TEST, "result"));
        final String serializedPath = SchemaPathCodec.serialize(schemaPath, schemaContext);
        Assert.assertEquals("./list-test:list1/list2/result", serializedPath);
    }

    @Test
    public void testDeserializationOfRelPathWithOneModule() {
        final SchemaPath expectedSchemaPath = SchemaPath.create(false,
                QName.create(MODULE_LIST_TEST, "list1"),
                QName.create(MODULE_LIST_TEST, "list2"));
        final SchemaPath deserializedPath = SchemaPathCodec.deserialize(
                "./list-test:list1/list2", schemaContext);
        Assert.assertEquals(expectedSchemaPath, deserializedPath);
    }

    @Test(expected = RestconfDocumentedException.class)
    public void testSerializationOfPathWithUnknownModule() {
        final SchemaPath schemaPath = SchemaPath.create(true,
                QName.create(MODULE_LIST_TEST, "top"),
                QName.create(MODULE_LIST_TEST, "list1"),
                QName.create(QNameModule.create(URI.create("invalid-module"),
                        Revision.of("2019-01-01")), "containerX"));
        SchemaPathCodec.serialize(schemaPath, schemaContext);
    }

    @Test(expected = RestconfDocumentedException.class)
    public void testDeserializationOfCorruptedPath() {
        SchemaPathCodec.deserialize("/list-test:top//list1/list2", schemaContext);
    }

    @Test(expected = RestconfDocumentedException.class)
    public void testDeserializationOfPathWithInvalidStart() {
        SchemaPathCodec.deserialize("list-test:top/list1/list2", schemaContext);
    }

    @Test
    public void testSerializationOfAbsPathWithTwoModules() {
        final SchemaPath schemaPath = SchemaPath.create(true,
                QName.create(MODULE_SERIALIZER_TEST_INCLUDED, "augmented-list"),
                QName.create(MODULE_SERIALIZER_TEST, "augmented-leaf"));
        final String serializedPath = SchemaPathCodec.serialize(schemaPath, schemaContext);
        Assert.assertEquals("/serializer-test-included:augmented-list/serializer-test:augmented-leaf",
                serializedPath);
    }

    @Test
    public void testDeserializationOfAbsPathWithTwoModules() {
        final SchemaPath expectedSchemaPath = SchemaPath.create(true,
                QName.create(MODULE_SERIALIZER_TEST_INCLUDED, "augmented-list"),
                QName.create(MODULE_SERIALIZER_TEST, "augmented-leaf"));
        final SchemaPath deserializedPath = SchemaPathCodec.deserialize(
                "/serializer-test-included:augmented-list/serializer-test:augmented-leaf", schemaContext);
        Assert.assertEquals(expectedSchemaPath, deserializedPath);
    }

    @Test
    public void testSerializationOfRelPathWithTwoModules() {
        final SchemaPath schemaPath = SchemaPath.create(false,
                QName.create(MODULE_SERIALIZER_TEST_INCLUDED, "augmented-list"),
                QName.create(MODULE_SERIALIZER_TEST, "augmented-leaf"),
                QName.create(MODULE_SERIALIZER_TEST, "virtual-leaf"));
        final String serializedPath = SchemaPathCodec.serialize(schemaPath, schemaContext);
        Assert.assertEquals("./serializer-test-included:augmented-list/serializer-test:augmented-leaf/virtual-leaf",
                serializedPath);
    }

    @Test
    public void testDeserializationOfRelPathWithTwoModules() {
        final SchemaPath expectedSchemaPath = SchemaPath.create(false,
                QName.create(MODULE_SERIALIZER_TEST_INCLUDED, "augmented-list"),
                QName.create(MODULE_SERIALIZER_TEST, "augmented-leaf"),
                QName.create(MODULE_SERIALIZER_TEST, "virtual-leaf"));
        final SchemaPath deserializedPath = SchemaPathCodec.deserialize("./serializer-test-included:"
                + "augmented-list/serializer-test:augmented-leaf/virtual-leaf", schemaContext);
        Assert.assertEquals(expectedSchemaPath, deserializedPath);
    }
}