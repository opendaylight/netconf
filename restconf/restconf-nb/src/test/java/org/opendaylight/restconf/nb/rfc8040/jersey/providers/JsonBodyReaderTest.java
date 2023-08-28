/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.FileNotFoundException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.restconf.nb.rfc8040.databind.JsonChildBody;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodes;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class JsonBodyReaderTest extends AbstractBodyReaderTest {
    private static EffectiveModelContext schemaContext;

    public JsonBodyReaderTest() {
        super(schemaContext);
    }

    @BeforeClass
    public static void initialization() throws FileNotFoundException {
        final var testFiles = loadFiles("/instanceidentifier/yang");
        testFiles.addAll(loadFiles("/modules"));
        schemaContext = YangParserTestUtils.parseYangFiles(testFiles);
    }

    @Test
    public void moduleSubContainerDataPostTest() throws Exception {
        final var dataSchemaNode = schemaContext.getDataChildByName(CONT_QNAME);
        final QName cont1QName = QName.create(dataSchemaNode.getQName(), "cont1");
        final YangInstanceIdentifier dataII = YangInstanceIdentifier.of(dataSchemaNode.getQName()).node(cont1QName);
        final String uri = "instance-identifier-module:cont";

        final var body = new JsonChildBody(
            JsonBodyReaderTest.class.getResourceAsStream("/instanceidentifier/json/json_sub_container.json"));
        final var payload = body.toPayload(
            SchemaInferenceStack.ofDataTreePath(schemaContext, CONT_QNAME).toInference());

        checkNormalizedNodePayload(payload);
        checkExpectValueNormalizeNodeContext(dataSchemaNode, payload, dataII);
    }

    @Test
    public void moduleSubContainerAugmentDataPostTest() throws Exception {
        final DataSchemaNode dataSchemaNode = schemaContext.getDataChildByName(CONT_QNAME);
        final Module augmentModule = schemaContext.findModules(XMLNamespace.of("augment:module")).iterator().next();
        final QName contAugmentQName = QName.create(augmentModule.getQNameModule(), "cont-augment");
        final YangInstanceIdentifier dataII = YangInstanceIdentifier.of(dataSchemaNode.getQName(), contAugmentQName);
        final String uri = "instance-identifier-module:cont";

        final var body = new JsonChildBody(
            XmlBodyReaderTest.class.getResourceAsStream("/instanceidentifier/json/json_augment_container.json"));
        checkNormalizedNodePayload(payload);
        checkExpectValueNormalizeNodeContext(dataSchemaNode, payload, dataII);
    }

    @Test
    public void moduleSubContainerChoiceAugmentDataPostTest() throws Exception {
        final DataSchemaNode dataSchemaNode = schemaContext.getDataChildByName(CONT_QNAME);
        final Module augmentModule = schemaContext.findModules(XMLNamespace.of("augment:module")).iterator().next();
        final QName augmentChoice1QName = QName.create(augmentModule.getQNameModule(), "augment-choice1");
        final QName augmentChoice2QName = QName.create(augmentChoice1QName, "augment-choice2");
        final QName containerQName = QName.create(augmentChoice1QName, "case-choice-case-container1");
        final YangInstanceIdentifier dataII = YangInstanceIdentifier.of(dataSchemaNode.getQName())
                .node(augmentChoice1QName).node(augmentChoice2QName).node(containerQName);
        final String uri = "instance-identifier-module:cont";

        final var body = new JsonChildBody(
            XmlBodyReaderTest.class.getResourceAsStream("/instanceidentifier/json/json_augment_choice_container.json"));
        checkNormalizedNodePayload(payload);
        checkExpectValueNormalizeNodeContext(dataSchemaNode, payload, dataII);
    }

    private static void checkExpectValueNormalizeNodeContext(final DataSchemaNode dataSchemaNode,
            final NormalizedNodePayload nnContext, final YangInstanceIdentifier dataNodeIdent) {
        assertEquals(dataSchemaNode, nnContext.getInstanceIdentifierContext().getSchemaNode());
        assertEquals(dataNodeIdent, nnContext.getInstanceIdentifierContext().getInstanceIdentifier());
        assertNotNull(NormalizedNodes.findNode(nnContext.getData(), dataNodeIdent));
    }
}
