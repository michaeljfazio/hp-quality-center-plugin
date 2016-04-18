package org.jenkinsci.plugins.qc.client;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;

import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.glassfish.jersey.message.MessageProperties;

public class QualityCenter {

	public static QualityCenter create(String url) {
		WebTarget target = ClientBuilder.newClient().target(url);
		target.property(MessageProperties.XML_SECURITY_DISABLE, Boolean.TRUE);
		/* target.register(new LoggingFilter()); */
		target.register(new SessionFilter());
		target.register(new ResponseFilter());
		return new QualityCenter(target);
	}

	private WebTarget root;

	private QualityCenter(WebTarget root) {
		this.root = root;
	}

	public List<String> domains() {
		List<String> domains = new ArrayList<String>();
		for (Schema.Domain domain : root.path("rest/domains").request().get(new DomainCollection())) {
			domains.add(domain.name);
		}
		return domains;
	}

	public List<String> projects(String domain) {
		List<String> projects = new ArrayList<String>();
		for (Schema.Project project : root.path("rest/domains").path(domain).path("projects").request()
				.get(new ProjectCollection())) {
			projects.add(project.name);
		}
		return projects;
	}

	public Entity create(String domain, String project, String resource) {
		WebTarget target = root.path("rest/domains").path(domain).path("projects").path(project).path(resource);
		return new Entity(target, new Schema.Entity());
	}

	public Query query(String domain, String project) {
		return new Query(root.path("rest/domains").path(domain).path("projects").path(project));
	}

	public boolean login(String username, String password) {
		HttpAuthenticationFeature feature = HttpAuthenticationFeature.basic(username, password);
		return root.path("/authentication-point/authenticate").register(feature).request(MediaType.TEXT_PLAIN_TYPE)
				.get().getStatus() == HttpURLConnection.HTTP_OK;
	}

	public boolean isAuthenticated() {
		return root.path("rest/is-authenticated").request().get().getStatus() == HttpURLConnection.HTTP_OK;
	}

	public void logout() {
		root.path("/authentication-point/logout").request().get();
	}

	private static class DomainCollection extends GenericType<List<Schema.Domain>> {
	}

	private static class ProjectCollection extends GenericType<List<Schema.Project>> {
	}

}
