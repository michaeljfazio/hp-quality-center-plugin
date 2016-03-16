package org.jenkinsci.plugins.qc.client;

import static javax.ws.rs.client.Entity.entity;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_XML_TYPE;

import java.io.InputStream;

import javax.ws.rs.client.WebTarget;

public class Entity {

	private final WebTarget target;
	private Schema.Entity entity;

	Entity(WebTarget target, Schema.Entity entity) {
		this.target = target;
		this.entity = entity;
	}
	
	public void setType(String type) {
		entity.type = type;
	}

	public String getType() {
		return entity.type;
	}

	public String get(String field) {
		return entity.field(field).value;
	}

	public void set(String field, String value) {
		entity.field(field).value = value;
	}

	public void add(String name, String value) {
		entity.add(name, value);
	}

	public void post() {
		this.entity = target.request(APPLICATION_XML_TYPE).post(entity(entity, APPLICATION_XML_TYPE),
				Schema.Entity.class);
	}

	public void put() {
		target.path(entity.field("id").value).request(APPLICATION_XML_TYPE).put(entity(entity, APPLICATION_XML_TYPE),
				Schema.Entity.class);
	}

	public void delete() {

	}

	public void get() {
		target.path(entity.field("id").value).request(APPLICATION_XML_TYPE).put(entity(entity, APPLICATION_XML_TYPE),
				Schema.Entity.class);
	}

	public void attach(String filename, InputStream in) {
		target.path(entity.field("id").value).path("attachments").request(APPLICATION_XML_TYPE).header("Slug", filename)
				.post(entity(in, APPLICATION_OCTET_STREAM_TYPE));
	}

}
