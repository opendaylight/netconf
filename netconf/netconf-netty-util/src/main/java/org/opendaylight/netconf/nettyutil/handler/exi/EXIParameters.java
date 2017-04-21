/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.exi;

import com.google.common.base.Preconditions;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.openexi.proc.common.AlignmentType;
import org.openexi.proc.common.EXIOptions;
import org.openexi.proc.common.EXIOptionsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class EXIParameters {
    private static final String EXI_PARAMETER_ALIGNMENT = "alignment";
    static final String EXI_PARAMETER_BYTE_ALIGNED = "byte-aligned";
    static final String EXI_PARAMETER_BIT_PACKED = "bit-packed";
    static final String EXI_PARAMETER_COMPRESSED = "compressed";
    static final String EXI_PARAMETER_PRE_COMPRESSION = "pre-compression";

    private static final String EXI_PARAMETER_FIDELITY = "fidelity";
    private static final String EXI_FIDELITY_DTD = "dtd";
    private static final String EXI_FIDELITY_LEXICAL_VALUES = "lexical-values";
    private static final String EXI_FIDELITY_COMMENTS = "comments";
    private static final String EXI_FIDELITY_PIS = "pis";
    private static final String EXI_FIDELITY_PREFIXES = "prefixes";

    private final EXIOptions options;
    private static final Logger LOG = LoggerFactory.getLogger(EXIParameters.class);

    private EXIParameters(final EXIOptions options) {
        this.options = Preconditions.checkNotNull(options);
    }

    @SuppressWarnings("checkstyle:FallThrough")
    public static EXIParameters fromXmlElement(final XmlElement root) throws EXIOptionsException {
        final EXIOptions options = new EXIOptions();
        final NodeList alignmentElements = root.getElementsByTagName(EXI_PARAMETER_ALIGNMENT);
        if (alignmentElements.getLength() > 0) {
            final Element alignmentElement = (Element) alignmentElements.item(0);
            final String alignmentTextContent = alignmentElement.getTextContent().trim();

            switch (alignmentTextContent) {
                case EXI_PARAMETER_BYTE_ALIGNED:
                    options.setAlignmentType(AlignmentType.byteAligned);
                    break;
                case EXI_PARAMETER_COMPRESSED:
                    options.setAlignmentType(AlignmentType.compress);
                    break;
                case EXI_PARAMETER_PRE_COMPRESSION:
                    options.setAlignmentType(AlignmentType.preCompress);
                    break;
                default:
                    LOG.warn("Unexpected value in alignmentTextContent: {} , using default value",
                            alignmentTextContent);
                case EXI_PARAMETER_BIT_PACKED:
                    options.setAlignmentType(AlignmentType.bitPacked);
                    break;
            }
        } else {
            options.setAlignmentType(AlignmentType.bitPacked);
        }

        final NodeList fidelityElements = root.getElementsByTagName(EXI_PARAMETER_FIDELITY);
        if (fidelityElements.getLength() > 0) {
            final Element fidelityElement = (Element) fidelityElements.item(0);
            if (fidelityElement.getElementsByTagName(EXI_FIDELITY_DTD).getLength() > 0) {
                options.setPreserveDTD(true);
            }
            if (fidelityElement.getElementsByTagName(EXI_FIDELITY_LEXICAL_VALUES).getLength() > 0) {
                options.setPreserveLexicalValues(true);
            }
            if (fidelityElement.getElementsByTagName(EXI_FIDELITY_COMMENTS).getLength() > 0) {
                options.setPreserveComments(true);
            }
            if (fidelityElement.getElementsByTagName(EXI_FIDELITY_PIS).getLength() > 0) {
                options.setPreservePIs(true);
            }
            if (fidelityElement.getElementsByTagName(EXI_FIDELITY_PREFIXES).getLength() > 0) {
                options.setPreserveNS(true);
            }
        }
        return new EXIParameters(options);
    }

    public EXIOptions getOptions() {
        return options;
    }
}
