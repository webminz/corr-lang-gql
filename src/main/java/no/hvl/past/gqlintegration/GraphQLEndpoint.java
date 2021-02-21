package no.hvl.past.gqlintegration;

import no.hvl.past.graph.Sketch;

import java.util.Objects;

public class GraphQLEndpoint {

    private final String url;
    private final Sketch schema;

    public GraphQLEndpoint(String url, Sketch schema) {
        this.url = url;
        this.schema = schema;
    }

    public String getUrl() {
        return url;
    }

    public Sketch getSchema() {
        return schema;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GraphQLEndpoint that = (GraphQLEndpoint) o;
        return url.equals(that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }
}
