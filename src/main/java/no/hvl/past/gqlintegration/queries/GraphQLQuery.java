package no.hvl.past.gqlintegration.queries;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.hvl.past.graph.Sketch;
import no.hvl.past.graph.trees.QueryTree;
import no.hvl.past.names.Name;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class GraphQLQuery implements QueryTree {

    private final GraphQLQueryNode root;

    public GraphQLQuery(GraphQLQueryNode.Builder builder) {
        this.root = builder.build();
    }

    public GraphQLQuery(GraphQLQueryNode root) {
        this.root = root;
    }

    public static GraphQLQuery parse(Sketch schema, Map<Name, String> plainNames, Name rootType, InputStream stream) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode root = objectMapper.readTree(stream);
        if (root.isObject()) {
            return new GraphQLQuery(GraphQLQueryNode.parse(schema, plainNames, rootType, root.get("query").asText(), null));
        } else if (root.isTextual()) {
            return new GraphQLQuery(GraphQLQueryNode.parse(schema, plainNames, rootType, root.asText(), null));
        } else {
            throw new IOException("Not a valid GraphQL Query: " + root.toPrettyString());
        }
    }

    @Override
    public GraphQLQueryNode root() {
        return root;
    }

    public String serialize(Map<Name, String> originalNames) {
        return root.serialize(originalNames);
    }

    @Override
    public String textualRepresentation() {
        StringBuilder result = new StringBuilder();
        root.print(result, 0, null);
        return result.toString();
    }
}
