package no.hvl.past.gqlintegration.queries;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import no.hvl.past.graph.Sketch;
import no.hvl.past.graph.trees.JsonTree;
import no.hvl.past.graph.trees.QueryHandler;
import no.hvl.past.graph.trees.QueryTree;
import no.hvl.past.graph.trees.Tree;
import no.hvl.past.names.Name;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public interface GraphQLQueryHandler extends QueryHandler {

    String endpointUrl();

    Sketch getSchema();

    Map<Name, String> plainNames();

    @Override
    default InputStream serialize(QueryTree queryTree) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        JsonGenerator generator = new JsonFactory().createGenerator(bos);
        generator.writeStartObject();
        generator.writeFieldName("query");
        if (queryTree instanceof GraphQLQuery) {
            GraphQLQuery query = (GraphQLQuery) queryTree;
            generator.writeString(query.serialize(plainNames()));
        } else {
            generator.writeString(queryTree.textualRepresentation());
        }
        generator.writeEndObject();
        generator.flush();
        bos.close();
        return new ByteArrayInputStream(bos.toByteArray());
    }

    @Override
    default Tree deserialize(InputStream inputStream) throws IOException {
        return JsonTree.fromInputStream( // TODO replace with a typed variant ???
                Name.identifier("Response@"+ LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).prefixWith(Name.identifier(endpointUrl())),
                Tree.ROOT,
                inputStream);
    }

    @Override
    default GraphQLQuery parse(InputStream inputStream) throws IOException {
        return GraphQLQuery.parse(getSchema(), plainNames(), Name.identifier("Schema"), inputStream);
    }



}
