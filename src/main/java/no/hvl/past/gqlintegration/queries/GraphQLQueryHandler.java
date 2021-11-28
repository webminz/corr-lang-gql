package no.hvl.past.gqlintegration.queries;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.hvl.past.gqlintegration.GraphQLEndpoint;
import no.hvl.past.gqlintegration.caller.IntrospectionQuery;
import no.hvl.past.graph.trees.*;
import no.hvl.past.names.Name;
import no.hvl.past.systems.MessageArgument;
import no.hvl.past.systems.MessageType;
import no.hvl.past.systems.ProcessingException;
import no.hvl.past.systems.QueryHandler;
import no.hvl.past.util.StreamExt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;

public abstract class GraphQLQueryHandler implements QueryHandler {

    private static final String ERRORS_FIELD = "errors";
    protected static final String RETURN_VALUE_FIELD = "data";
    private static final String QUERY_FIELD = "query";
    private static final String QUERY_OPERATION_FIELD = "operationName";
    private static final String INTROSPECTION_ROOT = "__schema";
    public static final String QUERY_VARIABLES_FIELD = "variables";
    private final GraphQLEndpoint endpoint;

    private Logger logger = LogManager.getLogger(getClass());
    protected GraphQLQueryHandler(GraphQLEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    ObjectMapper getObjectMapper() {
        return endpoint.getObjectMapper();
    }

    JsonFactory getJsonFactory() {
        return endpoint.getJsonFactory();
    }

    protected String displayName(Name formalName) {
        return endpoint.displayName(formalName);
    }

    // Public API

    public void serialize(TypedTree instance, OutputStream os) throws IOException {
        if (instance instanceof QueryTree) {
            JsonGenerator generator = getJsonFactory().createGenerator(os);
            generator.writeStartObject();
            generator.writeFieldName(QUERY_FIELD);
            generator.writeString(((QueryTree) instance).textualRepresentation());
            generator.writeEndObject();
            generator.flush();
        } else {
            new JsonSerializer(getJsonFactory()).serialize(os, instance, this::displayName);
        }
    }


    public TypedTree deserialize(InputStream inputStream) throws IOException, ProcessingException {
        // TODO support XML as well
        try {
            JsonNode jsonNode = getObjectMapper().readTree(inputStream);
            if (logger.isDebugEnabled()) {
                logger.debug("Received the following request: \n\n" + jsonNode.toPrettyString());
            }
            if (jsonNode.isObject()) {
                if (jsonNode.get(ERRORS_FIELD) != null) {
                    throw GraphQueryErrorMessage.parse(jsonNode.get(ERRORS_FIELD));
                }
                if (jsonNode.get(RETURN_VALUE_FIELD) != null) {
                    return tryParseInstance(jsonNode.get(RETURN_VALUE_FIELD));
                } else if (jsonNode.get(QUERY_FIELD) != null) {
                    String query = jsonNode.get(QUERY_FIELD).asText();
                    String opName = jsonNode.get(QUERY_OPERATION_FIELD) != null ? jsonNode.get(QUERY_OPERATION_FIELD).asText() : null;
                    JsonNode variables = parseVariables(jsonNode);
                    if (query.contains(INTROSPECTION_ROOT)) {
                        return new IntrospectionQuery(query, opName);
                    } else {
                        return GraphQLQueryParser.parse(this.endpoint, query, opName, variables);
                    }
                } else {
                    return tryParseInstance(jsonNode);
                }
            } else if (jsonNode.isTextual() && jsonNode.asText().contains(QUERY_FIELD)) {
                return GraphQLQueryParser.parse(this.endpoint, jsonNode.asText(), null, new ObjectNode(JsonNodeFactory.instance));
            }
        } catch (JsonParseException parseException) {
            // TODO try with XML
            throw new ProcessingException("Could not interpret the input: JSON parsing exception(\"" + parseException.getMessage() + "\")", parseException);
        }
        throw new ProcessingException("Could not interpret the input: unknown input format!");
    }

    // Helper methods



    private JsonNode parseVariables(JsonNode jsonNode) throws JsonProcessingException {
        if (jsonNode.get(QUERY_VARIABLES_FIELD) != null) {
            return jsonNode.get(QUERY_VARIABLES_FIELD);
        }
        return new ObjectNode(JsonNodeFactory.instance);
    }


    private TypedTree tryParseInstance(JsonNode rootNode) throws IOException {
        // TODO this should be a generic method of the core library
        JsonParser jsonParser = new JsonParser(getJsonFactory());
        if (rootNode.isObject()) {
            Iterator<String> fieldNames = rootNode.fieldNames();
            if (fieldNames.hasNext()) {
                String firstField = fieldNames.next();
                Optional<MessageType> firstMsg = endpoint.getMessage(firstField);
                if (firstMsg.isPresent()) {
                    TypedTree firstParsed = jsonParser.parseTyped(
                            rootNode.get(firstField),
                            Name.identifier(firstField),
                            endpoint.schema(),
                            firstMsg.get().arguments().filter(MessageArgument::isOutput).findFirst().get().asEdge().getTarget(),
                            this.endpoint::lookupField);
                    if (fieldNames.hasNext()) {
                        return mergeMultiResult(jsonParser, fieldNames, firstParsed, rootNode);
                    } else {
                        return firstParsed;
                    }
                } else {
                    throw new IOException("Could not find query/mutation with name '" + firstField + "'!");
                }
            }
        }
        throw new IOException("Could not parse the given input '" + rootNode.toPrettyString() + "'");
    }

    private TypedTree mergeMultiResult(JsonParser jsonParser, Iterator<String> fieldNames, TypedTree firstParsed, JsonNode rootNode) throws IOException {
        List<TypedTree> trees = new ArrayList<>();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            Optional<MessageType> message = endpoint.getMessage(field);
            if (message.isPresent()) {
                trees.add(jsonParser.parseTyped(
                        rootNode.get(field),
                        Name.identifier(field),
                        endpoint.schema(),
                        message.get().arguments().filter(MessageArgument::isOutput).findFirst().get().asEdge().getTarget(),
                        this.endpoint::lookupField));
            } else {
                throw new IOException("Could not find query/mutation with name '" + field + "'!");
            }
        }
        return TypedTree.fuseAsForrestAsBundle(trees, Name.identifier("data"), endpoint.schema());
    }






}
