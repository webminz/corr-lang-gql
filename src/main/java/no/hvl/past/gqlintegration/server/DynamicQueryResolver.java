package no.hvl.past.gqlintegration.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.SelectedField;
import no.hvl.past.gqlintegration.queries.GraphQLQuery;
import no.hvl.past.gqlintegration.queries.GraphQLQueryHandler;
import no.hvl.past.gqlintegration.queries.GraphQLQueryNode;
import no.hvl.past.graph.Sketch;
import no.hvl.past.graph.elements.Triple;
import no.hvl.past.names.Name;
import no.hvl.past.names.PrintingStrategy;
import no.hvl.past.util.Pair;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;


import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Adaptor from GraphQL-DataFetchers to our query handlers.
 */
public class DynamicQueryResolver implements DataFetcher {

    private Logger logger = Logger.getLogger(DynamicQueryResolver.class);

    private final GraphQLQueryHandler queryHandler;
    private final Sketch schema;
    private final Name root;
    private final Map<Name, String> nameToText;

    DynamicQueryResolver(
            GraphQLQueryHandler queryHandler,
            Sketch schema,
            Map<Name, String> nameToText,
            Name root) {
        this.queryHandler = queryHandler;
        this.schema = schema;
        this.nameToText = nameToText;
        this.root = root;
    }

    @Override
    public Object get(DataFetchingEnvironment environment) throws Exception {
        ObjectMapper om = new ObjectMapper();
        LocalDateTime start = LocalDateTime.now();
        GraphQLQuery query = translate(environment);
        logger.debug("Extracted query from GraphQL engine:\n" + query.textualRepresentation());
        JsonNode node = om.readTree(queryHandler.resolveAsStream(query)).get("data");
        logger.debug("Result that is handed back to the GraphQL engine:\n " + node.toPrettyString());
        logger.debug("Query handling in total took :" + Duration.between(start, LocalDateTime.now()).toMillis() + " ms");

        if (node.isArray()) {
            return jsonArrayToList(node);
        } else if (node.isObject()) {
            JsonNode field = node.get(environment.getField().getName());
            if (field.isArray()) {
                return jsonArrayToList(field);
            } else {
                return field;
            }
        } else {
            return node;
        }
    }

    @NotNull
    private Object jsonArrayToList(JsonNode node) {
        List<JsonNode> result = new ArrayList<>();
        Iterator<JsonNode> iterator = node.iterator();
        while (iterator.hasNext()) {
            result.add(iterator.next());
        }
        return result;
    }

    private GraphQLQuery translate(DataFetchingEnvironment environment) throws IOException {
        GraphQLQueryNode.Builder builder = new GraphQLQueryNode.Builder(Name.identifier(nameToText.get(root).toLowerCase()));
        if (environment.getField().getName().equals(nameToText.get(root).toLowerCase())) {
            translate(environment.getSelectionSet(), builder, root);
        } else {
            Pair<Name,Name> field = lookup(environment.getField().getName(), root);
            GraphQLQueryNode.Builder childBuilder = builder.startChild(field.getFirst());

            for (String argument : environment.getArguments().keySet()) {
                childBuilder.argument(argument, environment.getArguments().get(argument).toString());
            }
            translate(environment.getSelectionSet(), childBuilder, field.getRight());
        }
        return new GraphQLQuery(builder);
    }

    private void translate(DataFetchingFieldSelectionSet selectionSet, GraphQLQueryNode.Builder builder, Name currentType) throws IOException {
        Set<SelectedField> selectedFields = new HashSet<>(selectionSet.getFields());
        for (SelectedField selectedField : selectedFields) {
            Pair<Name,Name> fieldName = lookup(selectedField.getName(), currentType);
            GraphQLQueryNode.Builder childBuilder = builder.startChild(fieldName.getFirst());

            for (String argument : selectedField.getArguments().keySet()) {
                childBuilder.argument(argument, selectedField.getArguments().get(argument).toString());
            }

            translate(selectedField.getSelectionSet(), childBuilder,fieldName.getRight());
        }
    }

    /**
     * Retrieves the formal name of the edge representing the field
     * and the target type.
     *
     */
    private Pair<Name,Name> lookup(String fieldName, Name typeName) throws IOException {
        Optional<Triple> edge = schema.carrier().outgoing(typeName)
                .filter(e -> nameToText.get(e.getLabel()).equals(fieldName)).findFirst();
        if (edge.isPresent()) {
            return new Pair<>(edge.get().getLabel(), edge.get().getTarget());
        } else {
            throw new IOException("Field '" + fieldName + "' not found in type '" + typeName.print(PrintingStrategy.IGNORE_PREFIX) + "'!");
        }
    }


}
