# UrlBuilder
Utility class for constructing syntactically correct HTTP URLs using a fluent method-chaining API. It strives simply to be more robust then manually constructing URLs by string concatenation.

    new UrlBuilder("my.host.com", "foo").addPathSegment("bar").addParameter("a", "b").toString()

produces `http://my.host.com/foo/bar?a=b`

## Features
* Automatic slash management in paths. Slashes will be de-duped or added as necessary when using addPathSegment
* Automatic URL encoding for both path segments (preserving slashes) and query parameters
* '+'s in URL encoded values are replaced with '%20's
* Options for generation of fully-qualified, hostname relative, or protocol relative URLs
* Fluent method-chaining API

More examples at https://github.com/Widen/urlbuilder/blob/master/test/com/widen/util/UrlBuilderTest.java

## License
Licensed under Apache, Version 2.0. See [the license file](LICENSE.md) for details.
