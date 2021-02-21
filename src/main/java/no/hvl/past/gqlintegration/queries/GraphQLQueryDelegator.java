package no.hvl.past.gqlintegration.queries;

import com.fasterxml.jackson.databind.JsonNode;
import no.hvl.past.gqlintegration.caller.GraphQLCaller;
import no.hvl.past.gqlintegration.caller.GraphQLCallerFactory;
import no.hvl.past.graph.Sketch;
import no.hvl.past.graph.trees.JsonTree;
import no.hvl.past.graph.trees.QueryTree;
import no.hvl.past.graph.trees.Tree;
import no.hvl.past.names.Name;
import no.hvl.past.util.IOStreamUtils;
import no.hvl.past.util.Observer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class GraphQLQueryDelegator implements GraphQLQueryHandler {

    private final String url;
    private final Sketch sketch;
    private final Map<Name, String> plainNames;

    public GraphQLQueryDelegator(Sketch schema, String url, Map<Name, String> plainNames) {
        this.url = url;
        this.sketch = schema;
        this.plainNames = plainNames;
    }

    @Override
    public void handle(InputStream i, OutputStream o) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.connect();
        IOStreamUtils.copyOver(i, connection.getOutputStream());
        connection.getOutputStream().close();
        int responseCode = connection.getResponseCode();
        if ((responseCode / 100) == 2) {
            IOStreamUtils.copyOver(connection.getInputStream(), o);
            connection.disconnect();
        } else {
            String message = IOStreamUtils.readInputStreamAsString(connection.getErrorStream());
            connection.disconnect();
            throw new IOException("HTTP error! return code: " + responseCode + ", detail: " + message);
        }
    }

    @Override
    public String endpointUrl() {
        return url;
    }

    @Override
    public Sketch getSchema() {
        return sketch;
    }

    @Override
    public Map<Name, String> plainNames() {
        return plainNames;
    }


}
