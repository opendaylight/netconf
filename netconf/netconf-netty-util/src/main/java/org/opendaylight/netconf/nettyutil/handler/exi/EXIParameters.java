/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.exi;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.messages.NetconfStartExiMessage;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.shaded.exificient.core.CodingMode;
import org.opendaylight.netconf.shaded.exificient.core.EXIFactory;
import org.opendaylight.netconf.shaded.exificient.core.EncodingOptions;
import org.opendaylight.netconf.shaded.exificient.core.FidelityOptions;
import org.opendaylight.netconf.shaded.exificient.core.SchemaIdResolver;
import org.opendaylight.netconf.shaded.exificient.core.exceptions.EXIException;
import org.opendaylight.netconf.shaded.exificient.core.exceptions.UnsupportedOption;
import org.opendaylight.netconf.shaded.exificient.core.helpers.DefaultEXIFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class EXIParameters {
    private static final Logger LOG = LoggerFactory.getLogger(EXIParameters.class);

    static final String EXI_PARAMETER_ALIGNMENT = "alignment";
    private static final String EXI_PARAMETER_BYTE_ALIGNED = "byte-aligned";
    private static final String EXI_PARAMETER_BIT_PACKED = "bit-packed";
    private static final String EXI_PARAMETER_COMPRESSED = "compressed";
    private static final String EXI_PARAMETER_PRE_COMPRESSION = "pre-compression";

    static final String EXI_PARAMETER_FIDELITY = "fidelity";
    private static final String EXI_FIDELITY_DTD = "dtd";
    private static final String EXI_FIDELITY_LEXICAL_VALUES = "lexical-values";
    private static final String EXI_FIDELITY_COMMENTS = "comments";
    private static final String EXI_FIDELITY_PIS = "pis";
    private static final String EXI_FIDELITY_PREFIXES = "prefixes";

    static final String EXI_PARAMETER_SCHEMAS = "schemas";

    private static final SchemaIdResolver SCHEMA_RESOLVER = schemaId -> {
        if (schemaId == null) {
            return null;
        }
        if (schemaId.isEmpty()) {
            return EXISchema.BUILTIN.getGrammar();
        }
        if (schemaId.equals(EXISchema.BASE_1_1.getOption())) {
            return EXISchema.BASE_1_1.getGrammar();
        }

        throw new EXIException("Cannot resolve schema " + schemaId);
    };

    private static final EncodingOptions ENCODING_OPTIONS;

    static {
        final EncodingOptions opts = EncodingOptions.createDefault();
        try {
            opts.setOption(EncodingOptions.RETAIN_ENTITY_REFERENCE);
            opts.setOption(EncodingOptions.INCLUDE_OPTIONS);

            /**
             * NETCONF is XML environment, so the use of EXI cookie is not really needed. Adding it
             * decreases efficiency of encoding by adding human-readable 4 bytes "EXI$" to the head
             * of the stream. This is really useful, so let's output it now.
             */
            opts.setOption(EncodingOptions.INCLUDE_COOKIE);
        } catch (final UnsupportedOption e) {
            throw new ExceptionInInitializerError(e);
        }

        ENCODING_OPTIONS = opts;
    }

    private final FidelityOptions fidelityOptions;
    private final CodingMode codingMode;
    private final EXISchema schema;

    public EXIParameters(final CodingMode codingMode, final FidelityOptions fidelityOptions) {
        this(codingMode, fidelityOptions, EXISchema.NONE);
    }

    public EXIParameters(final CodingMode codingMode, final FidelityOptions fidelityOptions, final EXISchema schema) {
        this.fidelityOptions = requireNonNull(fidelityOptions);
        this.codingMode = requireNonNull(codingMode);
        this.schema = requireNonNull(schema);
    }

    @VisibleForTesting
    public static EXIParameters empty() {
        return new EXIParameters(CodingMode.BIT_PACKED, FidelityOptions.createDefault());
    }

    public static EXIParameters fromXmlElement(final XmlElement root) throws UnsupportedOption {
        final CodingMode coding;
        final NodeList alignmentElements = root.getElementsByTagName(EXI_PARAMETER_ALIGNMENT);
        if (alignmentElements.getLength() > 0) {
            final Element alignmentElement = (Element) alignmentElements.item(0);
            final String alignmentTextContent = alignmentElement.getTextContent().trim();

            switch (alignmentTextContent) {
                case EXI_PARAMETER_BYTE_ALIGNED:
                    coding = CodingMode.BYTE_PACKED;
                    break;
                case EXI_PARAMETER_COMPRESSED:
                    coding = CodingMode.COMPRESSION;
                    break;
                case EXI_PARAMETER_PRE_COMPRESSION:
                    coding = CodingMode.PRE_COMPRESSION;
                    break;
                case EXI_PARAMETER_BIT_PACKED:
                    coding = CodingMode.BIT_PACKED;
                    break;
                default:
                    LOG.warn("Unexpected value in alignmentTextContent: {} , using default value",
                            alignmentTextContent);
                    coding = CodingMode.BIT_PACKED;
                    break;
            }
        } else {
            coding = CodingMode.BIT_PACKED;
        }

        final FidelityOptions fidelity = FidelityOptions.createDefault();
        final NodeList fidelityElements = root.getElementsByTagName(EXI_PARAMETER_FIDELITY);
        if (fidelityElements.getLength() > 0) {
            final Element fidelityElement = (Element) fidelityElements.item(0);

            fidelity.setFidelity(FidelityOptions.FEATURE_DTD,
                fidelityElement.getElementsByTagName(EXI_FIDELITY_DTD).getLength() > 0);
            fidelity.setFidelity(FidelityOptions.FEATURE_LEXICAL_VALUE,
                fidelityElement.getElementsByTagName(EXI_FIDELITY_LEXICAL_VALUES).getLength() > 0);
            fidelity.setFidelity(FidelityOptions.FEATURE_COMMENT,
                fidelityElement.getElementsByTagName(EXI_FIDELITY_COMMENTS).getLength() > 0);
            fidelity.setFidelity(FidelityOptions.FEATURE_PI,
                fidelityElement.getElementsByTagName(EXI_FIDELITY_PIS).getLength() > 0);
            fidelity.setFidelity(FidelityOptions.FEATURE_PREFIX,
                fidelityElement.getElementsByTagName(EXI_FIDELITY_PREFIXES).getLength() > 0);
        }

        final EXISchema schema;
        final NodeList schemaElements = root.getElementsByTagName(EXI_PARAMETER_SCHEMAS);
        if (schemaElements.getLength() > 0) {
            final Element schemaElement = (Element) schemaElements.item(0);
            final String schemaName = schemaElement.getTextContent().trim();
            schema = EXISchema.forOption(schemaName);
            checkArgument(schema != null, "Unsupported schema name %s", schemaName);
        } else {
            schema = EXISchema.NONE;
        }

        return new EXIParameters(coding, fidelity, schema);
    }

    public EXIFactory getFactory() {
        final EXIFactory factory = DefaultEXIFactory.newInstance();
        factory.setCodingMode(codingMode);
        factory.setEncodingOptions(ENCODING_OPTIONS);
        factory.setFidelityOptions(fidelityOptions);
        factory.setGrammars(schema.getGrammar());
        factory.setSchemaIdResolver(SCHEMA_RESOLVER);
        return factory;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fidelityOptions, codingMode, schema);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof EXIParameters)) {
            return false;
        }
        final EXIParameters other = (EXIParameters) obj;
        return codingMode == other.codingMode && schema == other.schema
                && fidelityOptions.equals(other.fidelityOptions);
    }

    public @NonNull NetconfStartExiMessage toStartExiMessage(final String messageId) {
        final Document doc = XmlUtil.newDocument();
        final Element rpcElement = doc.createElementNS(XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0,
                XmlNetconfConstants.RPC_KEY);
        rpcElement.setAttributeNS(XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0,
                XmlNetconfConstants.MESSAGE_ID, messageId);

        // TODO draft http://tools.ietf.org/html/draft-varga-netconf-exi-capability-02#section-3.5.1 has no namespace
        // for start-exi element in xml
        final Element startExiElement = doc.createElementNS(XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_EXI_1_0,
            NetconfStartExiMessage.START_EXI);

        addAlignment(doc, startExiElement);
        addFidelity(doc, startExiElement);
        addSchema(doc, startExiElement);

        rpcElement.appendChild(startExiElement);

        doc.appendChild(rpcElement);
        return new NetconfStartExiMessage(doc);
    }

    private void addAlignment(final Document doc, final Element startExiElement) {
        final Element alignmentElement = doc.createElementNS(XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_EXI_1_0,
            EXIParameters.EXI_PARAMETER_ALIGNMENT);

        alignmentElement.setTextContent(getAlignment());
        startExiElement.appendChild(alignmentElement);
    }

    private String getAlignment() {
        switch (codingMode) {
            case BIT_PACKED:
                return EXI_PARAMETER_BIT_PACKED;
            case BYTE_PACKED:
                return EXI_PARAMETER_BYTE_ALIGNED;
            case COMPRESSION:
                return EXI_PARAMETER_COMPRESSED;
            case PRE_COMPRESSION:
                return EXI_PARAMETER_PRE_COMPRESSION;
            default:
                throw new IllegalStateException("Unhandled coding mode " + codingMode);
        }
    }

    private void addFidelity(final Document doc, final Element startExiElement) {
        final List<Element> fidelityElements = new ArrayList<>(5);
        createFidelityElement(doc, fidelityElements,
            fidelityString(FidelityOptions.FEATURE_COMMENT, EXI_FIDELITY_COMMENTS));
        createFidelityElement(doc, fidelityElements, fidelityString(FidelityOptions.FEATURE_DTD, EXI_FIDELITY_DTD));
        createFidelityElement(doc, fidelityElements,
            fidelityString(FidelityOptions.FEATURE_LEXICAL_VALUE, EXI_FIDELITY_LEXICAL_VALUES));
        createFidelityElement(doc, fidelityElements, fidelityString(FidelityOptions.FEATURE_PI, EXI_FIDELITY_PIS));
        createFidelityElement(doc, fidelityElements,
            fidelityString(FidelityOptions.FEATURE_PREFIX, EXI_FIDELITY_PREFIXES));

        if (!fidelityElements.isEmpty()) {
            final Element fidelityElement = doc.createElementNS(
                XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_EXI_1_0, EXIParameters.EXI_PARAMETER_FIDELITY);
            for (final Element element : fidelityElements) {
                fidelityElement.appendChild(element);
            }
            startExiElement.appendChild(fidelityElement);
        }
    }

    private String fidelityString(final String feature, final String string) {
        return fidelityOptions.isFidelityEnabled(feature) ? string : null;
    }

    private void addSchema(final Document doc, final Element startExiElement) {
        if (schema != null) {
            final Element child = doc.createElementNS(XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_EXI_1_0,
                EXIParameters.EXI_PARAMETER_SCHEMAS);
            child.setTextContent(schema.name());
            startExiElement.appendChild(child);
        }
    }

    private static void createFidelityElement(final Document doc, final List<Element> elements, final String fidelity) {
        if (fidelity != null) {
            elements.add(doc.createElementNS(XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_EXI_1_0, fidelity));
        }
    }
}
