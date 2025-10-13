package org.opendaylight.restconf.server.api;

/**
 * Common interface for stream encoding identifiers used by both
 * {@code ietf-restconf-monitoring} (RFC8040) and
 * {@code ietf-subscribed-notifications} (RFC8639).
 */
public interface StreamEncoding {
    /**
     * Encoding token used in the YANG models, e.g. {@code "json"},
     * {@code "xml"}, {@code "encode-json"}, or {@code "encode-xml"}.
     *
     * @return canonical name of this encoding
     */
    String localName();
}
