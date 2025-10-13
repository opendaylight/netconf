package org.opendaylight.restconf.server.spi;

import org.opendaylight.restconf.server.api.StreamEncoding;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeJson$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeXml$I;
import org.opendaylight.yangtools.yang.common.QName;

/**
 * RFC8639 stream encodings for subscribed notifications.
 */
public enum SubscriptionEncoding implements StreamEncoding {
    ENCODE_JSON(EncodeJson$I.QNAME),
    ENCODE_XML(EncodeXml$I.QNAME);

    private final QName qname;

    SubscriptionEncoding(final QName qname) {
        this.qname = qname;
    }

    public QName qname() {
        return qname;
    }

    @Override
    public String localName() {
        return qname.getLocalName();
    }
}
