package io.deffun.didier.data;

import org.intellij.lang.annotations.Language;

public final class GqlSchemas {
    private GqlSchemas() {
    }

    @Language("graphql")
    public static final String SIMPLE_SCHEMA = """
                type Product {
                    id: ID!
                    name: String!
                    description: String
                    category: ProductCategory!
                }
                type ProductCategory {
                    id: ID!
                    name: String!
                }
                type Query {
                    products: [Product!]
                }
            """;
}
