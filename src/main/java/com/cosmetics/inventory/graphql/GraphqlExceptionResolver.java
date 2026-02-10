package com.cosmetics.inventory.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

import graphql.schema.DataFetchingEnvironment;

import java.util.NoSuchElementException;

@Component
public class GraphqlExceptionResolver extends DataFetcherExceptionResolverAdapter {
	@Override
	protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
		Throwable root = ex;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}

		if (root instanceof IllegalArgumentException) {
			return GraphqlErrorBuilder.newError(env)
					.message(root.getMessage())
					.errorType(ErrorType.BAD_REQUEST)
					.build();
		}

		if (root instanceof IllegalStateException) {
			return GraphqlErrorBuilder.newError(env)
					.message(root.getMessage())
					.errorType(ErrorType.BAD_REQUEST)
					.build();
		}

		if (root instanceof NoSuchElementException) {
			return GraphqlErrorBuilder.newError(env)
					.message("Not found")
					.errorType(ErrorType.NOT_FOUND)
					.build();
		}

		return null;
	}
}
