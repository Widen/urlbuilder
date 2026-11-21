package com.widen.urlbuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Writes the parts of a URL the way S3 and CloudFront read them, for {@link S3UrlBuilder} and
 * {@link CloudfrontUrlBuilder}.
 */
final class AwsUrls {

    private static final Pattern LEADING_SLASHES = Pattern.compile("^/+");

    private AwsUrls() {
    }

    /**
     * Returns the key without its leading slashes, as S3 and CloudFront keys are given.
     */
    static String stripLeadingSlashes(String key) {
        return key == null ? "" : LEADING_SLASHES.matcher(key).replaceFirst("");
    }

    /**
     * Splits an object key into path segments for {@link UrlBuilder#withExactPathSegments(List)} the way the AWS
     * SDKs write it, after dropping leading slashes: the first slash of a run separates segments, and the rest
     * belong to the next segment, which encodes them as %2F, so {@code a///b} becomes {@code a/%2F%2Fb}. Raw
     * {@code //} in a path would change the key behind an HTTP client or proxy that merges slashes.
     */
    static List<String> objectKeySegments(String key) {
        String stripped = stripLeadingSlashes(key);
        List<String> segments = new ArrayList<>();
        if (stripped.isEmpty()) {
            return segments;
        }

        StringBuilder segment = new StringBuilder();
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (c != '/') {
                segment.append(c);
                continue;
            }

            segments.add(segment.toString());
            segment.setLength(0);
            while (i + 1 < stripped.length() && stripped.charAt(i + 1) == '/') {
                segment.append('/');
                i++;
            }
        }
        segments.add(segment.toString());

        return segments;
    }

    /**
     * Returns the host in lower case and without the default port of the scheme, which is how HTTP clients send it
     * in the Host header. Some clients, such as Go's, send the host exactly as the URL writes it, so the S3 and
     * CloudFront builders write it this way, and their signatures cover it.
     */
    static String hostAsClientsSendIt(String host, boolean ssl) {
        String lowerCase = host.toLowerCase(Locale.ROOT);
        String defaultPort = ssl ? ":443" : ":80";
        return lowerCase.endsWith(defaultPort) ? lowerCase.substring(0, lowerCase.length() - defaultPort.length()) : lowerCase;
    }

}
