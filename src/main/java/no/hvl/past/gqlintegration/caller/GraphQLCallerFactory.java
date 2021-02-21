package no.hvl.past.gqlintegration.caller;

public abstract class GraphQLCallerFactory {

    private static GraphQLCallerFactory instance;

    public abstract GraphQLCaller create();

    public static void initalize() {
        instance = new GraphQLCallerFactory() {
            @Override
            public GraphQLCaller create() {
                return new GraphQLCallerImpl();
            }
        };
    }

    public static void initializeForTest() {
        //instance = new GraphQLCallerFactory() {
//            @Override
//            public GraphQLCaller create() {
//                return new GraphQLCallerMock();
//            }
//        };
    }

    public static GraphQLCallerFactory getInstance() {
        if (instance == null) {
            initalize();
        }
        return instance;
    }

}
