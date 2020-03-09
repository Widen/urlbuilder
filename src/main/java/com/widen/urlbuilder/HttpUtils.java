package com.widen.urlbuilder;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.Normalizer;

public class HttpUtils
{
    // Guidelines for generating this header: https://tools.ietf.org/html/rfc6266#appendix-D
    public static String createContentDispositionHeader(String type, String filename)
    {
        // Most browsers don't normally support UTF-8 in HTTP headers (HTTP officially supports only ISO-8859-1). In
        // practice, S3 only supports ASCII characters, so strip everything from the filename out not in the ASCII
        // range.
        // In addition, many browsers do not support escape sequences or percent encoding properly either, so strip out
        // the following characters: \ " %
        String asciiFilename = Normalizer
            .normalize(filename, Normalizer.Form.NFD)
            .replaceAll("[^\\x20-\\x7E[\\\\\"%]]", "");

        // Create the base header value.
        String header = String.format("%s; filename=\"%s\"", type, asciiFilename);

        // For clients that support RFC 5987, we can URL encode the UTF-8 encoded filename and pass it in as an
        // additional parameter. If the ASCII filename differs from the given filename, add the additional parameter.
        if (!asciiFilename.equals(filename))
        {
            try
            {
                String utf8EncodedFilename = URLEncoder.encode(filename, "UTF-8").replaceAll("\\+", "%20");
                header += String.format("; filename*=UTF-8''%s", utf8EncodedFilename);
            }
            catch (UnsupportedEncodingException e)
            {
                throw new RuntimeException(e);
            }
        }

        return header;
    }
}
