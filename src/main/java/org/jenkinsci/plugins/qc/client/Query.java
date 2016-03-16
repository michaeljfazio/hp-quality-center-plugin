package org.jenkinsci.plugins.qc.client;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;

import org.glassfish.jersey.uri.UriComponent;
import org.glassfish.jersey.uri.UriComponent.Type;

public class Query {

	private static final int PAGE_SIZE = 50;

	private final WebTarget root;
	private String resource;
	private String filter;
	private String[] fields;

	Query(WebTarget root) {
		this.root = root;
	}

	public Query resource(String resource) {
		this.resource = resource;
		return this;
	}

	public Query filter(String filter, Object... params) {
		this.filter = "{" + MessageFormat.format(filter, params) + "}";
		return this;
	}

	public Query fields(String... fields) {
		this.fields = fields;
		return this;
	}

	public List<Entity> execute() {
		int page = 0;
		int count = 0;
		List<Entity> results = new ArrayList<Entity>();

		WebTarget target = root.path(resource);

		if (filter != null) {
			String encoded = UriComponent.encode(filter, Type.QUERY_PARAM_SPACE_ENCODED);
			target = target.queryParam("query", encoded);
		}

		if (fields != null) {
			// TODO
		}

		do {
			List<Schema.Entity> entities = target.queryParam("page-size", PAGE_SIZE)
					.queryParam("start-index", (page++ * PAGE_SIZE) + 1).request().get(new EntityCollectionType());
			for(Schema.Entity entity: entities) {
				results.add(new Entity(root.path(resource), entity));
			}
			count = entities.size();
		} while (count > 0);

		return results;
	}

	private static class EntityCollectionType extends GenericType<List<Schema.Entity>> { }

}
