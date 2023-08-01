/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2021 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.restconf.api.query.FieldsParam;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.yangtools.yang.common.QName;

/**
 * Unit test for {@link WriterFieldsTranslator}.
 */
@RunWith(MockitoJUnitRunner.class)
public class WriterFieldsTranslatorTest extends AbstractFieldsTranslatorTest<Set<QName>> {
    @Override
    protected List<Set<QName>> translateFields(final InstanceIdentifierContext context, final FieldsParam fields) {
        return WriterFieldsTranslator.translate(context, fields);
    }

    @Override
    protected void assertSimplePath(final List<Set<QName>> result) {
        assertEquals(1, result.size());
        assertEquals(Set.of(LIBRARY_QNAME), result.get(0));
    }

    @Override
    protected void assertDoublePath(final List<Set<QName>> result) {
        assertEquals(1, result.size());
        assertEquals(Set.of(LIBRARY_QNAME, PLAYER_QNAME), result.get(0));
    }

    @Override
    protected void assertSubPath(final List<Set<QName>> result) {
        assertEquals(4, result.size());
        assertEquals(Set.of(LIBRARY_QNAME), result.get(0));
        assertEquals(Set.of(ARTIST_QNAME), result.get(1));
        assertEquals(Set.of(ALBUM_QNAME), result.get(2));
        assertEquals(Set.of(NAME_QNAME), result.get(3));
    }

    @Override
    protected void assertChildrenPath(final List<Set<QName>> result) {
        assertEquals(4, result.size());
        assertEquals(Set.of(LIBRARY_QNAME), result.get(0));
        assertEquals(Set.of(ARTIST_QNAME), result.get(1));
        assertEquals(Set.of(ALBUM_QNAME), result.get(2));
        assertEquals(Set.of(NAME_QNAME), result.get(3));
    }

    @Override
    protected void assertNamespace(final List<Set<QName>> result) {
        assertEquals(1, result.size());
        assertEquals(Set.of(AUGMENTED_LIBRARY_Q_NAME), result.get(0));
    }

    @Override
    protected void assertMultipleChildren1(final List<Set<QName>> result) {
        assertEquals(3, result.size());
        assertEquals(Set.of(SERVICES_Q_NAME), result.get(0));
        assertEquals(Set.of(TYPE_OF_SERVICE_Q_NAME, INSTANCE_Q_NAME), result.get(1));
        assertEquals(Set.of(INSTANCE_NAME_Q_NAME, PROVIDER_Q_NAME), result.get(2));
    }

    @Override
    protected void assertMultipleChildren2(final List<Set<QName>> result) {
        assertEquals(3, result.size());
        assertEquals(Set.of(SERVICES_Q_NAME), result.get(0));
        assertEquals(Set.of(TYPE_OF_SERVICE_Q_NAME, INSTANCE_Q_NAME), result.get(1));
        assertEquals(Set.of(INSTANCE_NAME_Q_NAME, PROVIDER_Q_NAME), result.get(2));
    }

    @Override
    protected void assertMultipleChildren3(final List<Set<QName>> result) {
        assertEquals(3, result.size());
        assertEquals(Set.of(SERVICES_Q_NAME), result.get(0));
        assertEquals(Set.of(TYPE_OF_SERVICE_Q_NAME, INSTANCE_Q_NAME, NEXT_DATA_Q_NAME), result.get(1));
        assertEquals(Set.of(INSTANCE_NAME_Q_NAME, NEXT_SERVICE_Q_NAME), result.get(2));
    }

    @Override
    protected void assertMultipleChildren4(final List<Set<QName>> result) {
        assertEquals(3, result.size());
        assertEquals(Set.of(SERVICES_Q_NAME), result.get(0));
        assertEquals(Set.of(TYPE_OF_SERVICE_Q_NAME, INSTANCE_Q_NAME, NEXT_DATA_Q_NAME), result.get(1));
        assertEquals(Set.of(INSTANCE_NAME_Q_NAME, PROVIDER_Q_NAME, NEXT_SERVICE_Q_NAME), result.get(2));
    }

    @Override
    protected void assertMultipleChildren5(final List<Set<QName>> result) {
        assertEquals(3, result.size());
        assertEquals(Set.of(SERVICES_Q_NAME), result.get(0));
        assertEquals(Set.of(TYPE_OF_SERVICE_Q_NAME, INSTANCE_Q_NAME, NEXT_DATA_Q_NAME), result.get(1));
        assertEquals(Set.of(INSTANCE_NAME_Q_NAME, PROVIDER_Q_NAME, NEXT_SERVICE_Q_NAME), result.get(2));
    }

    @Override
    protected void assertAugmentedChild(final List<Set<QName>> result) {
        assertEquals(2, result.size());
        assertEquals(Set.of(PLAYER_QNAME), result.get(0));
        assertEquals(Set.of(SPEED_Q_NAME), result.get(1));
    }

    @Override
    protected void assertListFieldUnderList(final List<Set<QName>> result) {
        assertEquals(2, result.size());
        assertEquals(Set.of(SERVICES_Q_NAME), result.get(0));
        assertEquals(Set.of(INSTANCE_Q_NAME), result.get(1));
    }

    @Override
    protected void assertKeyedList(final List<Set<QName>> result) {
        assertEquals(3, result.size());
        assertEquals(Set.of(LIBRARY_QNAME), result.get(0));
        assertEquals(Set.of(ARTIST_QNAME), result.get(1));
        assertEquals(Set.of(NAME_QNAME), result.get(2));
    }

    @Override
    protected void assertLeafList(final List<Set<QName>> result) {
        assertEquals(1, result.size());
        assertEquals(Set.of(PROTOCOLS_Q_NAME), result.get(0));
    }

    @Override
    protected void assertDuplicateNodes1(final List<Set<QName>> result) {
        assertEquals(3, result.size());
        assertEquals(Set.of(BAR_Q_NAME, BAZ_Q_NAME), result.get(0));
        assertEquals(Set.of(ALPHA_Q_NAME, BETA_Q_NAME), result.get(1));
        assertEquals(Set.of(GAMMA_Q_NAME), result.get(2));
    }

    @Override
    protected void assertDuplicateNodes2(final List<Set<QName>> result) {
        assertEquals(3, result.size());
        assertEquals(Set.of(BAR_Q_NAME, BAZ_Q_NAME), result.get(0));
        assertEquals(Set.of(ALPHA_Q_NAME, BETA_Q_NAME), result.get(1));
        assertEquals(Set.of(DELTA_Q_NAME, EPSILON_Q_NAME), result.get(2));
    }
}
