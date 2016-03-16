package org.jenkinsci.plugins.qc.client;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.NewCookie;

class SessionFilter implements ClientRequestFilter, ClientResponseFilter {

	private final Map<String, NewCookie> cookies = new HashMap<String, NewCookie>();

	@Override
	public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
		cookies.putAll(responseContext.getCookies());
	}

	@Override
	public void filter(ClientRequestContext requestContext) throws IOException {
		for (NewCookie cookie : cookies.values()) {
			requestContext.getHeaders().add("Cookie", cookie);
		}
	}

}
