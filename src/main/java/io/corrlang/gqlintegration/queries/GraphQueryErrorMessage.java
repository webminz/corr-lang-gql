package io.corrlang.gqlintegration.queries;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Objects;
import io.corrlang.domain.ProcessingException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Stack;

public class GraphQueryErrorMessage extends ProcessingException {

    private static final String MSG = "Query Processing resulted in an error! See log for details!";

    public static GraphQueryErrorMessage parse(JsonNode jsonNode) {
        GraphQueryErrorMessage result = new GraphQueryErrorMessage();
        for (JsonNode err : jsonNode) {
            if (err.has("message")) {
                result.addError(err.get("message").textValue());
                // TODO other information as well
            }
        }

        return result;
    }

    public void clear() {
        this.errors.clear();
    }

    public static class Error {
        private final String message;
        private final int column;
        private final int row;
        private final Stack<String> path;

        public Error(String message, int column, int row, Stack<String> path) {
            this.message = message;
            this.column = column;
            this.row = row;
            this.path = path;
        }

        void write(JsonGenerator generator) throws IOException {
            generator.writeStartObject();
            generator.writeFieldName("message");
            generator.writeString(message);
            if (column >= 0 && row >= 0) {
                generator.writeFieldName("locations");
                generator.writeStartObject();
                generator.writeFieldName("line");
                generator.writeNumber(row);
                generator.writeFieldName("column");
                generator.writeNumber(column);
                generator.writeEndObject();
            }

            if (!path.isEmpty()) {
                generator.writeFieldName("path");
                generator.writeStartArray();
                for (String s : path) {
                    generator.writeString(s);
                }
                generator.writeEndArray();
            }
            generator.writeEndObject();
        }


        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Error error = (Error) o;
            return column == error.column && row == error.row && Objects.equal(message, error.message);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(message, column, row);
        }
    }

    public boolean isErrorFree() {
        return errors.isEmpty();
    }

    private final LinkedHashSet<Error> errors;

    public GraphQueryErrorMessage() {
        super(MSG);
        this.errors = new LinkedHashSet<>();
    }

    public void addError(String message) {
        this.errors.add(new Error(message, -1, -1, new Stack<>()));
    }

    public void addError(String message, int line, int column) {
        this.errors.add(new Error(message, column, line, new Stack<>()));
    }

    public void addError(String message, int line, int column, Stack<String> queryTree) {
        this.errors.add(new Error(message, column, line, queryTree));
    }

    public void write(JsonGenerator generator) throws  IOException {
        generator.writeStartArray();
        for (Error e : errors) {
            e.write(generator);
        }
        generator.writeEndArray();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GraphQueryErrorMessage that = (GraphQueryErrorMessage) o;
        return this.errors.equals(that.errors);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(errors);
    }

    @Override
    protected String printDetails() {
        if (isErrorFree()) {
            return "";
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            JsonGenerator generator = JsonFactory.builder().build().createGenerator(bos);
            generator.setPrettyPrinter(new DefaultPrettyPrinter());
            write(generator);
            generator.close();
            return bos.toString();
        } catch (IOException e) {
            return "";
        }

    }






}
