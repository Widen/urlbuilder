package com.widen.urlbuilder


import spock.lang.Specification
import spock.lang.Unroll

class UrlBuilderSpec extends Specification {
    def "Happy path"() {
        when:
        def builder = new UrlBuilder("my.host.com", "foo").addPathSegment("bar").addParameter("a", "b")

        then:
        builder.toString() == "http://my.host.com/foo/bar?a=b"
    }

    @Unroll
    def "Round-trip parse and to string"() {
        when:
        def builder = new UrlBuilder(url)

        then:
        builder.toString() == url

        where:
        url << [
            "http://my.host.com:8080/bar?a=b#foo",
            "http://my.host.com/bar?a=b#foo",
            "https://my.host.com/bar?a=b#foo",
            "https://my.host.com:8080/bar?a=b&c=d"
        ]
    }

    def "Query parameters as map"() {
        when:
        def builder = new UrlBuilder('https://my.host.com/bar?a=x&b=2&c=3&c=4&a&d#foo')

        then:
        builder.queryParameters.size() == 4
        builder.queryParameters.a == ['x', '']
        builder.queryParameters.b == ['2']
        builder.queryParameters.c == ['3', '4']
        builder.queryParameters.d == ['']
    }
}
