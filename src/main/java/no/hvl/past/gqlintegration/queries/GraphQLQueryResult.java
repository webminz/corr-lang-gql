package no.hvl.past.gqlintegration.queries;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;


public interface GraphQLQueryResult {

    class Impl implements GraphQLQueryResult {
        private final JsonNode jsonNode;

        public Impl(JsonNode jsonNode) {
            this.jsonNode = jsonNode;
        }

        @Override
        public JsonNode jsonRepresentation() {
            return jsonNode;
        }

        @Override
        public String textRepresentation() {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                return objectMapper.writeValueAsString(jsonNode);
            } catch (JsonProcessingException e) {
                // TODO logging
                return "";
            }
        }

        @Override
        public void toOutputStream(OutputStream outputStream) {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                objectMapper.writeValue(outputStream, jsonNode);
            } catch (IOException e) {
                // TODO logging

            }
        }
    }

    JsonNode jsonRepresentation();

    String textRepresentation();

    void toOutputStream(OutputStream outputStream);

}
