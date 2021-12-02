package io.corrlang.gqlintegration;

import io.corrlang.plugins.techspace.TechSpace;

public class GraphQLTechSpace implements TechSpace {

     static GraphQLTechSpace INSTANCE = new GraphQLTechSpace();
     static final String KEY = "GRAPH_QL";

     private GraphQLTechSpace() {

     }

     @Override
     public String ID() {
        return KEY;
    }
}
