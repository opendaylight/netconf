/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.operations;

import static java.util.Objects.requireNonNull;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assume.assumeThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.w3c.dom.Document;

class FilterContentValidatorTest {
    private static final int TEST_CASE_COUNT = 13;
    private static final Pattern LIST_ENTRY_PATTERN =
            Pattern.compile("(?<listName>.*)\\[\\{(?<keys>(.*)(, .*)*)\\}\\]");
    private static final Pattern KEY_VALUE_PATTERN =
            Pattern.compile("(?<key>\\(.*\\).*)=(?<value>.*)");
    private FilterContentValidator validator;

    static Stream<Arguments> data() throws Exception {
        final var result = new ArrayList<Arguments>();
        final var expected = Files.readAllLines(
            Path.of(FilterContentValidatorTest.class.getResource("/filter/expected.txt").toURI()));
        if (expected.size() != TEST_CASE_COUNT) {
            throw new IllegalArgumentException("Number of lines in results file must be same as test case count");
        }
        for (int i = 1; i <= TEST_CASE_COUNT; i++) {
            final var document = XmlUtil.readXmlToDocument(FilterContentValidatorTest.class.getResourceAsStream(
                    "/filter/f" + i + ".xml"));
            result.add(arguments(document, expected.get(i - 1)));
        }
        return result.stream();
    }

    @BeforeEach
    void setUp() {
        final var databind = DatabindContext.ofModel(
            YangParserTestUtils.parseYangResources(FilterContentValidatorTest.class,
                "/yang/filter-validator-test-mod-0.yang", "/yang/filter-validator-test-augment.yang",
                "/yang/mdsal-netconf-mapping-test.yang"));

        validator = new FilterContentValidator(() -> databind);
    }

    @ParameterizedTest
    @MethodSource("data")
    void testValidateSuccess(final Document filterContent, final String expected) throws DocumentedException {
        final var filter = XmlElement.fromDomDocument(filterContent);
        assumeThat(expected, startsWith("success"));

        final String expId = expected.replace("success=", "");
        final var actual = validator.validate(filter);
        assertEquals(fromString(expId), actual);
    }

    @ParameterizedTest
    @MethodSource("data")
    void testValidateError(final Document filterContent, final String expected) {
        final var filter = XmlElement.fromDomDocument(filterContent);
        assumeThat(expected, startsWith("error"));

        final var ex = assertThrows(DocumentedException.class, () -> validator.validate(filter));
        final String expectedExceptionClass = expected.replace("error=", "");
        assertEquals(expectedExceptionClass, ex.getClass().getName());
    }

    private static YangInstanceIdentifier fromString(final String input) {
        //remove first /
        final String yid = input.substring(1);
        final var pathElements = Arrays.asList(yid.split("/"));
        final var builder = YangInstanceIdentifier.builder();
        //if not specified, PathArguments inherit namespace and revision from previous PathArgument
        QName prev = null;
        for (var pathElement : pathElements) {
            final var matcher = LIST_ENTRY_PATTERN.matcher(pathElement);
            if (matcher.matches()) {
                prev = parseListEntry(builder, prev, matcher);
            } else {
                final var qname = createNodeQName(prev, pathElement);
                builder.node(qname);
                prev = qname;
            }
        }
        return builder.build();
    }

    private static QName parseListEntry(final YangInstanceIdentifier.InstanceIdentifierBuilder builder,
                                        final QName prev, final Matcher matcher) {
        final var keys = new HashMap<QName, Object>();
        final String listName = matcher.group("listName");
        final QName listQName = createNodeQName(prev, listName);
        final String keysString = matcher.group("keys");
        for (var str : keysString.split(",")) {
            final Matcher keyMatcher = KEY_VALUE_PATTERN.matcher(str.trim());
            if (keyMatcher.matches()) {
                keys.put(QName.create(keyMatcher.group("key")), keyMatcher.group("value"));
            }
        }
        builder.nodeWithKey(listQName, keys);
        return prev;
    }

    private static QName createNodeQName(final QName prev, final String input) {
        try {
            return QName.create(input);
        } catch (IllegalArgumentException e) {
            return QName.create(requireNonNull(prev), input);
        }
    }
}
