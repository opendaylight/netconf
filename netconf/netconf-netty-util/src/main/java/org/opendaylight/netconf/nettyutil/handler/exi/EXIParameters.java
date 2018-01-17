/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.exi;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.siemens.ct.exi.CodingMode;
import com.siemens.ct.exi.EXIFactory;
import com.siemens.ct.exi.EncodingOptions;
import com.siemens.ct.exi.FidelityOptions;
import com.siemens.ct.exi.exceptions.UnsupportedOption;
import com.siemens.ct.exi.helpers.DefaultEXIFactory;
import java.util.Objects;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public EXIParameters(final CodingMode codingMode, final FidelityOptions fidelityOptions) {
        this.fidelityOptions = Preconditions.checkNotNull(fidelityOptions);
        this.codingMode = Preconditions.checkNotNull(codingMode);
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

        return new EXIParameters(coding, fidelity);
    }

    public EXIFactory getFactory() {
        final EXIFactory factory = DefaultEXIFactory.newInstance();
        factory.setCodingMode(codingMode);
        factory.setEncodingOptions(ENCODING_OPTIONS);
        factory.setFidelityOptions(fidelityOptions);
        return factory;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fidelityOptions, codingMode);
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
        return codingMode == other.codingMode && fidelityOptions.equals(other.fidelityOptions);
    }

    String getAlignment() {
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

    private String fidelityString(final String feature, final String string) {
        return fidelityOptions.isFidelityEnabled(feature) ? string : null;
    }

    String getPreserveComments() {
        return fidelityString(FidelityOptions.FEATURE_COMMENT, EXI_FIDELITY_COMMENTS);
    }

    String getPreserveDTD() {
        return fidelityString(FidelityOptions.FEATURE_DTD, EXI_FIDELITY_DTD);
    }

    String getPreserveLexicalValues() {
        return fidelityString(FidelityOptions.FEATURE_LEXICAL_VALUE, EXI_FIDELITY_LEXICAL_VALUES);
    }

    String getPreservePIs() {
        return fidelityString(FidelityOptions.FEATURE_PI, EXI_FIDELITY_PIS);
    }

    String getPreservePrefixes() {
        return fidelityString(FidelityOptions.FEATURE_PREFIX, EXI_FIDELITY_PREFIXES);
    }
}
