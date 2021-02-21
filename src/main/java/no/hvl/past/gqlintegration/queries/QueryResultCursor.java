package no.hvl.past.gqlintegration.queries;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;


public interface QueryResultCursor {

    void copyAndRenameSubtree(JsonGenerator target, GraphQLQueryNode node) throws IOException;
}
