/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.Comparator;
import java.util.Set;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.server.api.ServerErrorInfo.JsonWritable;
import org.opendaylight.restconf.server.api.ServerErrorInfo.XmlWritable;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.UnresolvedQName.Unqualified;
import org.opendaylight.yangtools.yang.common.XMLNamespace;

/**
 * The content of {@code error-info} {@code anydata}.
 */
// FIXME: String here is legacy coming from RestconfError. This really should be a FormattableBody or similar, i.e.
//        structured content which itself is formattable -- unlike FormattableBody, though, it needs to be defined as
//        being formatted to a output. This format should include writing.
// FIXME: given that the normalized-node-based FormattableBody lives in server.spi, this should probably be an interface
//        implemented in at server.spi level.
@Beta
@NonNullByDefault
public sealed interface ServerErrorInfo permits JsonWritable, XmlWritable {
    /**
     * A {@link ServerErrorInfo} which can be written into a {@link JsonWriter}.
     */
    non-sealed interface JsonWritable extends ServerErrorInfo {

        void writeTo(JsonWriter writer) throws IOException;
    }

    /**
     * A {@link ServerErrorInfo} which can be written into a {@link XMLStreamWriter}.
     */
    non-sealed interface XmlWritable extends ServerErrorInfo {

        void writeTo(XMLStreamWriter writer) throws XMLStreamException;
    }

    /**
     * Common interface for standardized {@link ServerErrorInfo}s.
     */
    sealed interface Standard extends JsonWritable, XmlWritable {
        /**
         * A {@link ServerErrorInfo} applicable to {@link ErrorTag#BAD_ATTRIBUTE}, {@link ErrorTag#MISSING_ATTRIBUTE}
         * and {@link ErrorTag#UNKNOWN_ATTRIBUTE} as per RFC6241.
         *
         * @param badElement name of the enclosing element
         * @param badAttribute name of the attribute
         */
        record AttributeErrorInfo(Unqualified badElement, Unqualified badAttribute) implements Standard {
            public AttributeErrorInfo {
                requireNonNull(badElement);
                requireNonNull(badAttribute);
            }

            @Override
            public void writeTo(final JsonWriter writer) throws IOException {
                writer.name("bad-attribute").value(badAttribute.getLocalName())
                      .name("bad-element").value(badElement.getLocalName());
            }

            @Override
            public void writeTo(final XMLStreamWriter writer) throws XMLStreamException {
                writer.writeStartElement("bad-attribute");
                writer.writeCharacters(badAttribute.getLocalName());
                writer.writeEndElement();
                writer.writeStartElement("bad-element");
                writer.writeCharacters(badElement.getLocalName());
                writer.writeEndElement();
            }

            @Override
            public String toString() {
                return MoreObjects.toStringHelper(this)
                    .add("bad-element", badElement.getLocalName())
                    .add("bad-attribute", badAttribute.getLocalName())
                    .toString();
            }
        }

        /**
         * A {@link ServerErrorInfo} applicable to {@link ErrorTag#BAD_ELEMENT}, {@link ErrorTag#MISSING_ELEMENT}
         * and {@link ErrorTag#UNKNOWN_ELEMENT} as per RFC6241.
         *
         * @param badElement name of the element
         */
        record ElementErrorInfo(Unqualified badElement) implements Standard {
            public ElementErrorInfo {
                requireNonNull(badElement);
            }

            @Override
            public void writeTo(final JsonWriter writer) throws IOException {
                writer.name("bad-element").value(badElement.getLocalName());
            }

            @Override
            public void writeTo(final XMLStreamWriter writer) throws XMLStreamException {
                writer.writeStartElement("bad-element");
                writer.writeCharacters(badElement.getLocalName());
                writer.writeEndElement();
            }

            @Override
            public String toString() {
                return MoreObjects.toStringHelper(this).add("bad-element", badElement.getLocalName()).toString();
            }
        }

        /**
         * A {@link ServerErrorInfo} applicable to {@link ErrorTag#UNKNOWN_NAMESPACE} as per RFC6241.
         *
         * @param badElement name of the enclosing element
         * @param badNamespace name of the namespace
         */
        record NamespaceErrorInfo(Unqualified badElement, XMLNamespace badNamespace) implements Standard {
            public NamespaceErrorInfo {
                requireNonNull(badElement);
                requireNonNull(badNamespace);
            }

            @Override
            public void writeTo(final JsonWriter writer) throws IOException {
                writer.name("bad-element").value(badElement.getLocalName())
                      .name("bad-namespace").value(badNamespace.toString());
            }

            @Override
            public void writeTo(final XMLStreamWriter writer) throws XMLStreamException {
                writer.writeStartElement("bad-element");
                writer.writeCharacters(badElement.getLocalName());
                writer.writeEndElement();
                writer.writeStartElement("bad-namespace");
                writer.writeCharacters(badNamespace.toString());
                writer.writeEndElement();
            }

            @Override
            public String toString() {
                return MoreObjects.toStringHelper(this)
                    .add("bad-element", badElement.getLocalName())
                    .add("bad-namespace", badNamespace)
                    .toString();
            }
        }

        /**
         * A {@link ServerErrorInfo} applicable to {@link ErrorTag#LOCK_DENIED}.
         */
        record SessionErrorInfo(Uint32 sessionId) implements Standard {
            public SessionErrorInfo {
                requireNonNull(sessionId);
            }

            @Override
            public void writeTo(final JsonWriter writer) throws IOException {
                writer.name("session-id").value(sessionId.toJava());
            }

            @Override
            public void writeTo(final XMLStreamWriter writer) throws XMLStreamException {
                writer.writeStartElement("session-id");
                writer.writeCharacters(sessionId.toCanonicalString());
                writer.writeEndElement();
            }

            @Override
            public String toString() {
                return MoreObjects.toStringHelper(this).add("session-id", sessionId).toString();
            }
        }

        /**
         * A {@link ServerErrorInfo} applicable to {@link ErrorTag#PARTIAL_OPERATION}.
         */
        @Deprecated(since = "RFC6241")
        record PartialOperationErrorInfo(
                ImmutableSet<Unqualified> okElements,
                ImmutableSet<Unqualified> errElements,
                ImmutableSet<Unqualified> noopElements) {
            public PartialOperationErrorInfo {
                // Note: sorted to ease debugging
                okElements = sortElements(okElements);
                errElements = sortElements(errElements);
                noopElements = sortElements(noopElements);
            }

            @Override
            public String toString() {
                final var helper = MoreObjects.toStringHelper(this);
                appendElements(helper, "ok-elements", okElements);
                appendElements(helper, "err-elements", errElements);
                appendElements(helper, "noop-elements", noopElements);
                return helper.toString();
            }

            private static void appendElements(final ToStringHelper helper, final String name,
                    final Set<Unqualified> elements) {
                if (!elements.isEmpty()) {
                    helper.add(name, Collections2.transform(elements, Unqualified::getLocalName));
                }
            }

            private static ImmutableSet<Unqualified> sortElements(final ImmutableSet<Unqualified> elements) {
                return elements.stream()
                    .sorted(Comparator.comparing(Unqualified::getLocalName))
                    .collect(ImmutableSet.toImmutableSet());
            }
        }
    }
}
