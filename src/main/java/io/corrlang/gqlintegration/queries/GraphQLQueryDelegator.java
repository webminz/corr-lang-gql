package io.corrlang.gqlintegration.queries;

import io.corrlang.gqlintegration.GraphQLEndpoint;
import no.hvl.past.util.IOStreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class GraphQLQueryDelegator extends GraphQLQueryHandler {

    private final String url;

    public GraphQLQueryDelegator(GraphQLEndpoint system) {
        super(system);
        this.url = system.url();
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


}
