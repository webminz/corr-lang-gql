package no.hvl.past.gqlintegration.caller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.common.collect.Sets;
import graphql.introspection.IntrospectionResultToSchema;
import graphql.language.Document;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import no.hvl.past.attributes.TypedVariables;
import no.hvl.past.gqlintegration.queries.GraphQLQuery;
import no.hvl.past.graph.Graph;
import no.hvl.past.graph.GraphImpl;
import no.hvl.past.graph.elements.Triple;
import no.hvl.past.graph.trees.QueryNode;
import no.hvl.past.graph.trees.QueryTree;
import no.hvl.past.logic.Formula;
import no.hvl.past.names.Name;
import no.hvl.past.util.StreamExt;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class IntrospectionQuery implements QueryTree {

    public static final String GRAPHQL_META_INFO_PREFIX = "__";
    private HttpURLConnection connection;

    private static final String JSON_RESULT_CONTAINER_OBJ = "data";

    private static final String INTROSPECTION_QUERY = "query IntrospectionQuery {\n" +
            "    __schema {\n" +
            "      queryType { name }\n" +
            "      mutationType { name }\n" +
            "      subscriptionType { name }\n" +
            "      types {\n" +
            "        ...FullType\n" +
            "      }\n" +
            "      directives {\n" +
            "        name\n" +
            "        description\n" +
            "        locations\n" +
            "        args {\n" +
            "          ...InputValue\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "\n" +
            "  fragment FullType on __Type {\n" +
            "    kind\n" +
            "    name\n" +
            "    description\n" +
            "    fields(includeDeprecated: true) {\n" +
            "      name\n" +
            "      description\n" +
            "      args {\n" +
            "        ...InputValue\n" +
            "      }\n" +
            "      type {\n" +
            "        ...TypeRef\n" +
            "      }\n" +
            "      isDeprecated\n" +
            "      deprecationReason\n" +
            "    }\n" +
            "    inputFields {\n" +
            "      ...InputValue\n" +
            "    }\n" +
            "    interfaces {\n" +
            "      ...TypeRef\n" +
            "    }\n" +
            "    enumValues(includeDeprecated: true) {\n" +
            "      name\n" +
            "      description\n" +
            "      isDeprecated\n" +
            "      deprecationReason\n" +
            "    }\n" +
            "    possibleTypes {\n" +
            "      ...TypeRef\n" +
            "    }\n" +
            "  }\n" +
            "\n" +
            "  fragment InputValue on __InputValue {\n" +
            "    name\n" +
            "    description\n" +
            "    type { ...TypeRef }\n" +
            "    defaultValue\n" +
            "  }\n" +
            "\n" +
            "fragment TypeRef on __Type {\n" +
            "    kind\n" +
            "    name\n" +
            "    ofType {\n" +
            "      kind\n" +
            "      name\n" +
            "      ofType {\n" +
            "        kind\n" +
            "        name\n" +
            "        ofType {\n" +
            "          kind\n" +
            "          name\n" +
            "          ofType {\n" +
            "            kind\n" +
            "            name\n" +
            "            ofType {\n" +
            "              kind\n" +
            "              name\n" +
            "              ofType {\n" +
            "                kind\n" +
            "                name\n" +
            "                ofType {\n" +
            "                  kind\n" +
            "                  name\n" +
            "                }\n" +
            "              }\n" +
            "            }\n" +
            "          }\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "  }";

    private final String query;

    private final String operationName;

    private final Map<String, Object> variables;

    public String getQuery() {
        return query;
    }

    public Optional<String> getOperationName() {
        return Optional.ofNullable(operationName);
    }

    public Optional<Map<String, Object>> getVariables() {
        return Optional.ofNullable(variables);
    }

    public IntrospectionQuery() {
        this.query = IntrospectionQuery.INTROSPECTION_QUERY;
        this.operationName = null;
        this.variables = null;
    }

    public IntrospectionQuery(String query, String operationName, Map<String, Object> variables) {
        this.query = query;
        this.operationName = operationName;
        this.variables = variables;
    }

    public GraphQLSchema getGraphQLSchema(String endpoint) throws IOException {
        final JsonNode result = this.executeQuery(endpoint, INTROSPECTION_QUERY);
        final ObjectMapper mapper = new ObjectMapper();
        @SuppressWarnings("unchecked") final Map<String, Object> schemaDefinition = mapper.convertValue(result, HashMap.class);
        final Document document = new IntrospectionResultToSchema().createSchemaDefinition(schemaDefinition);
        final SchemaParser parser = new SchemaParser();
        final TypeDefinitionRegistry schema = parser.buildRegistry(document);
        final SchemaGenerator generator = new SchemaGenerator();
        return generator.makeExecutableSchema(schema, RuntimeWiring.newRuntimeWiring().build());
    }

    private JsonNode executeQuery(final String endpoint, final String query) throws IOException {
        this.setupConnection(new URL(endpoint));
        this.getConnection().connect();
        BufferedWriter outputWriter = new BufferedWriter(new OutputStreamWriter(this.getConnection().getOutputStream()));
        outputWriter.write(JsonNodeFactory.instance.objectNode().put("query", query).toString());
        outputWriter.flush();
        outputWriter.close();
        int responseCode = getConnection().getResponseCode();
        switch (responseCode) {
                    case HttpURLConnection.HTTP_OK:

                    BufferedReader inputReader = new BufferedReader(new InputStreamReader(this.getConnection().getInputStream()));
                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = inputReader.readLine()) != null) {
                        result.append(line);
                        result.append(System.lineSeparator());
                    }
                    inputReader.close();
                    this.getConnection().disconnect();
                    final ObjectMapper mapper = new ObjectMapper();
                    final JsonNode root = mapper.readTree(result.toString());
                    return root.get(IntrospectionQuery.JSON_RESULT_CONTAINER_OBJ);

                    case HttpURLConnection.HTTP_BAD_REQUEST:
                    case HttpURLConnection.HTTP_INTERNAL_ERROR:

                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(getConnection().getErrorStream()));
                    StringBuilder errorResult = new StringBuilder();
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorResult.append(errorLine);
                        errorResult.append(System.lineSeparator());
                    }
                    errorReader.close();
                    this.getConnection().disconnect();
                    throw new RuntimeException(errorResult.toString());
            default:
                throw new RuntimeException("unexpected HTTP code " + responseCode);
        }

    }

    private void setupConnection(final URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);
        connection.setDoInput(true);
        this.setConnection(connection);
    }

    private void setConnection(final HttpURLConnection connection) {
        this.connection = connection;
    }

    private HttpURLConnection getConnection() {
        return connection;
    }

    @Override
    public Stream<QueryNode.Root> queryRoots() {
        return Stream.empty();
    }

    @Override
    public String textualRepresentation() {
        return query;
    }

    @Override
    public Graph codomain() {
        // TODO create a full space Graph of the the GraphQL schema...
        return new GraphImpl(Name.identifier("GraphQL SDL"), Sets.newHashSet(Triple.node(Name.identifier("Type"))));
    }

    @Override
    public Name getName() {
        return Name.identifier("IntrospectionQuery");
    }

    @Override
    public boolean isInfinite() {
        return false;
    }
}
