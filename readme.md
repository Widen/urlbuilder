UrlBuilder
==========

Utility class for constructing syntactically correct HTTP URLs using a fluent method-chaining API. It strives simply to be more robust then manually constructing URLs by string concatenation.

    new UrlBuilder("my.host.com", "foo").addPathSegment("bar").addParameter("a", "b").toString()
    
produces `http://my.host.com/foo/bar?a=b`

More examples at https://github.com/Widen/urlbuilder/blob/master/test/com/widen/util/UrlBuilderTest.java

Licensed under Apache, Version 2.0.
