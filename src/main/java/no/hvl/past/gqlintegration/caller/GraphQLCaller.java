package no.hvl.past.gqlintegration.caller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import graphql.schema.GraphQLSchema;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This interface defines the methods for a utility class which coordinates the querying of GraphQL endpoints.
 */
public interface GraphQLCaller {


    String GRAPHQL_META_INFO_PREFIX = "__";

    /**
     * This method performs an introspection query against the defined endpoint.
     * The result will be converted into a GraphQL schema.
     *
     * @param endpoint Given url of an endpoint.
     * @return Converted response of the introspection query as GraphQL schema.
     * @throws IOException If the connection can not be established or the query can not be executed.
     */
    GraphQLSchema getGraphQLSchema(String endpoint) throws IOException;

    /**
     * This method executes a given entry against a given endpoint.
     *
     * @param endpoint Given url of an endpoint.
     * @param query    Given query.
     * @return Json response from the endpoint.
     * @throws IOException If the connection can not be established or the query can not be executed.
     */
    JsonNode executeQuery(String endpoint, String query) throws IOException;
//
//    /**
//     * This method translates a query within a given root type of a given field with given arguments
//     * and a given selection of subfields into an amount of queries
//     * against all original endpoints with the defined field.
//     *
//     * @param rootType              Given root type.
//     * @param fieldName             Given root field.
//     * @param arguments             Given arguments of the query.
//     * @param selectionSetArguments Given selection of subfields.
//     * @return Map of the endpoint url and the generated query for this endpoint.
//     */
//    Map<String, String> generateQueries(String rootType, String fieldName, Map<String, Object> arguments, Set<String> selectionSetArguments);
//
//    /**
//     * This method translates the response of an original endpoint into the schema of the integrated endpoint.
//     *
//     * @param endpoint              Given original endpoint.
//     * @param type                  Given type within the original endpoint.
//     * @param response              Given response to be translated.
//     * @param selectionSetArguments Given selection of subfields initially queried by the client.
//     * @return Translated json object with the fieldnames of the integrated endpoint
//     * including only fields selected by the client.
//     */
//    JsonNode normalizeResults(final String endpoint, final String type, JsonNode response, Set<String> selectionSetArguments);
//
//    /**
//     * Fetches the result type for a given field defined by its qualified name including the models endpoints,
//     * the types name and the fields name.
//     *
//     * @param endpoint  Given endpoint of the model of the searched type.
//     * @param typeName  Given name of the owner type from the field.
//     * @param fieldName Given name of the field.
//     * @return The result type of the described field.
//     */
//    OutputType getResultType(final String endpoint, final String typeName, final String fieldName);
//
//    /**
//     * Fetches the name of the given model, which is identified by its endpoint.
//     *
//     * @param endpoint Given endpoint of the model.
//     * @return The defined name of the given model.
//     */
//    String getModelName(final String endpoint);


//
//    private Set<String> translate(final OutputModel model, final OutputField field, final Set<String> selectionSetArguments) {
//        Set<String> result = new HashSet<>();
//        Set<String> selectedFields = new HashSet<>();
//        selectedFields.addAll(selectionSetArguments);
//        Optional<OutputType> searchResultType = model.getTypes().stream().filter(type -> type.getName().equals(field.getResultTypeName())).findAny();
//        if (!searchResultType.isPresent()) {
//            throw new Error("Could not retrieve result type for field " + field.getName());
//        }
//        final OutputType resultType = searchResultType.get();
//        for (String current : selectedFields) {
//            Optional<OutputField> resultField = resultType.getFields().stream().filter(resultTypeFields -> resultTypeFields.getName().equals(current)).findAny();
//            if (resultField.isPresent()) {
//                String resultFieldName = resultField.get().getName();
//                String targetFieldName = resultField.get().getOriginalName();
//                result.add(targetFieldName);
//                Set<String> nextLevel = new HashSet<>();
//                for (String fieldName : selectedFields) {
//                    if (fieldName.startsWith(resultFieldName + "/")) {
//                        nextLevel.add(fieldName.substring(fieldName.indexOf("/") + 1));
//                    }
//                }
//                result.addAll(this.translate(model, resultField.get(), nextLevel).stream().map(str -> targetFieldName + "/" + str).collect(Collectors.toList()));
//            }
//        }
//        if (resultType.getEqualityExpression() != null) {
//            Collection<VariableExpression> variables = resultType.getEqualityExpression().involvedVariables();
//            for (VariableExpression variable : variables) {
//                if (variable.getModelName().equals(model.getName())) {
//                    result.add(variable.getFieldName());
//                }
//            }
//        }
//        return result;
//    }
//
//    @Override
//    public Map<String, String> generateQueries(final String rootType, final String fieldName, final Map<String, Object> arguments, final Set<String> selectionSetArguments) {
//        Collection<OutputType> types = this.models.stream().flatMap(model -> model.getTypes().stream()).filter(type -> type.getName().equals(rootType)).collect(Collectors.toList());
//        Collection<OutputField> fields = types.stream().flatMap(type -> type.getFields().stream()).filter(field -> field.getName().equals(fieldName)).collect(Collectors.toList());
//        Map<String, String> result = new HashMap<>();
//        for (OutputField field : fields) {
//            Optional<OutputModel> searchModel = this.models.stream().filter(model -> model.getTypes().stream()
//                    .anyMatch(type -> type.getFields().contains(field))).findAny();
//            if (!searchModel.isPresent()) {
//                throw new Error("Could not find output model for the field " + field.getName());
//            }
//            OutputModel model = searchModel.get();
//            String query = rootType.toLowerCase() +
//                    queryStart +
//                    System.lineSeparator() +
//                    this.generateQuery(field.getOriginalName(), this.translate(model, field, selectionSetArguments),
//                            field, arguments) +
//                    queryEnd;
//            result.put(model.getEndpoint(), query);
//        }
//        return result;
//    }
//
//    private String generateQuery(final String baseType, final Set<String> selectionSetArguments,
//                                 final OutputField field, final Map<String, Object> arguments) {
//        StringBuilder result = new StringBuilder();
//        result.append(baseType);
//        if (!arguments.isEmpty()) {
//            result.append(argumentsStart);
//        }
//        boolean first = true;
//        for (String key : arguments.keySet()) {
//            if (!first) {
//                result.append(argumentsSeparator);
//            }
//            first = false;
//            Optional<OutputArgument> argument = field.getArguments().stream()
//                    .filter(arg -> arg.getName().equals(key)).findAny();
//            if (!argument.isPresent()) {
//                throw new Error("Could not resolve field with name " + key + " within field " + field.getName());
//            }
//            result.append(argument.get().getOriginalName());
//            result.append(argumentValueStartSign);
//            if (argument.get().isString()) {
//                result.append("\"");
//            }
//            result.append(arguments.get(key).toString());
//            if (argument.get().isString()) {
//                result.append("\"");
//            }
//        }
//        if (!arguments.isEmpty()) {
//            result.append(argumentsEnd);
//        }
//        if (!selectionSetArguments.isEmpty()) {
//            result.append(queryStart);
//            for (String current : selectionSetArguments) {
//                if (current.contains("/"))
//                    continue;
//                result.append(System.lineSeparator());
//                Set<String> children = selectionSetArguments.stream()
//                        .filter(selection -> selection.startsWith(current + "/"))
//                        .map(child -> child.substring(child.indexOf("/") + 1)).collect(Collectors.toSet());
//                if (children.size() > 0) {
//                    result.append(this.generateQuery(current, children, field, new HashMap<>()));
//                } else {
//                    result.append(current);
//                }
//            }
//            result.append(queryEnd);
//        }
//        return result.toString();
//    }

}
