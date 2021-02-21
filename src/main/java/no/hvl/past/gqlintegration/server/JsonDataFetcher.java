package no.hvl.past.gqlintegration.server;

import com.fasterxml.jackson.databind.JsonNode;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class JsonDataFetcher implements DataFetcher {


    @Override
    public Object get(DataFetchingEnvironment environment) throws Exception {
        Object source = environment.getSource();
        if (source instanceof JsonNode) {
            JsonNode json = (JsonNode) source;
            if (json.isObject()) {
                JsonNode result = json.get(environment.getField().getName());
                if (result.isTextual()) {
                    return result.asText();
                } else if (result.isIntegralNumber()) {
                    return result.asInt();
                } else if (result.isFloatingPointNumber()) {
                    return result.asDouble();
                } else if (result.isBoolean()) {
                    return result.asBoolean();
                }
                return result;
            } else if (json.isArray()) {
                Collection<JsonNode> result = new ArrayList<>();
                Iterator<JsonNode> iterator = json.iterator();
                while (iterator.hasNext()) {
                    result.add(iterator.next().get(environment.getField().getName()));
                }
                return result;
            }
        }
        // TODO exception handling
        return null;
    }
}
