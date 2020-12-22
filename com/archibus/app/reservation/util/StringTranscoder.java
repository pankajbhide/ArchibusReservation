package com.archibus.app.reservation.util;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.*;

/**
 * Utility class. Provides methods to transcode strings. Used by AppointmentBinder to convert
 * Appointment GUIDs.
 *
 * @author Yorik Gerlo
 * @since 21.2
 */
public final class StringTranscoder {

    /**
     * Private default constructor: utility class is non-instantiable.
     */
    private StringTranscoder() {
        super();
    }

    /**
     * Encode a string from base 16 (hex) to base64.
     *
     * @param uid the base16 encoded string
     * @return the base64 encoded string
     * @throws DecoderException when the transcode fails
     */
    public static String transcodeHexToBase64(final String uid) throws DecoderException {
        // The Appointment GUID must be base64 encoded for EWS. The IcalUid property is base16
        // (hex), so convert it to base64.
        final byte[] buffer = Hex.decodeHex(uid.toCharArray());
        return new String(Base64.encodeBase64(buffer));
    }

    /**
     * Remove HTML and carriage returns from a piece of text.
     * 
     * @param text the text to strip HTML from
     * @return the text without HTML
     */
    public static String stripHtml(final String text) {
        // remove all html tags and convert non-blank spaces to normal spaces
        String plainText = text.replaceAll(" <br>", "").replaceAll("\\<[^>]*>", "");
        plainText = plainText.replaceAll("&nbsp;", " ");
        // also remove carriage return, we only consider line feeds
        plainText = plainText.replaceAll("\r", "");
        return plainText;
    }

}
