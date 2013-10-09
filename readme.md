UrlBuilder
==========

Utility class for constructing syntactically correct HTTP URLs using a fluent method-chaining API. It strives simply to be more robust then manually constructing URLs by string concatenation.

    new UrlBuilder("my.host.com", "foo").addPathSegment("bar").addParameter("a", "b").toString()

produces `http://my.host.com/foo/bar?a=b`

Features
--------
* Automatic slash management in paths. Slashes will be de-duped or added as necessary when using addPathSegment
* Automatic URL encoding for both path segments (preserving slashes) and query parameters
* '+'s in URL encoded values are replaced with '%20's
* Options for generation of fully-qualified, hostname relative, or protocol relative URLs
* Fluent method-chaining API

More examples at https://github.com/Widen/urlbuilder/blob/master/test/com/widen/util/UrlBuilderTest.java

* Version 0.9 - Initial release
* Version 0.9.1 - Add S3UrlBuilder
* Version 0.9.3 - Add content-disposition overrides to S3UrlBuilder
* Version 0.9.4 - Add Encoder interface for plugable URL encoding schemes
* Version 0.9.5
    * Single String constructor now uses java.net.URL to reconstruct the URL; for old behavior use `new UrlBuilder().withPath("foo")`
    * added decode() method to Encoder interface
    * added getQueryParameterString() to get value of URL after "?"
* Version 0.9.6 - Add custom S3 api endpoints
* Version 0.9.7 - change getPath() to return standard slash-separated value instead of List.toString() comma-separated value
* Version 0.9.8 - add additional getters for properties ssl, hostname, port, fragment, generation mode
* Version 0.10.1 - EXPERIENTIAL
	* add CloudfrontUrlBuilder for generating signed URLs
	* crypto utilities for reading PEM (text) and DER (binary) RSA keys
	* added dependency on bouncycastle (only required for reading PEM keys)
	* Individual URLBuilder parameters can now have distinct encoders

Licensed under Apache, Version 2.0.
