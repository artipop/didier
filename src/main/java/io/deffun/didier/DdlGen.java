package io.deffun.didier;

import graphql.schema.GraphQLSchema;

import java.io.OutputStream;

public sealed interface DdlGen permits LiquibaseDdlGen {
    void generate(GraphQLSchema schema, OutputStream outputStream);
}
