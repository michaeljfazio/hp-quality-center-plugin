package org.jenkinsci.plugins.qc.client;

import java.io.IOException;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.jenkinsci.plugins.qc.client.Schema.QCRestException;

public class ResponseFilter implements ClientResponseFilter {

	private static final Object LINE_SEPARATOR = "\n";

	private final Unmarshaller unmarshaller;

	public ResponseFilter() {
		try {
			JAXBContext ctx = JAXBContext.newInstance(Schema.QCRestException.class);
			unmarshaller = ctx.createUnmarshaller();
		} catch (JAXBException e) {
			throw new Error(e);
		}
	}

	@Override
	public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
		if (responseContext.getStatus() == Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()) {
			if (responseContext.hasEntity()) {
				try {
					QCRestException ex = (QCRestException) unmarshaller.unmarshal(responseContext.getEntityStream());

					StringBuilder builder = new StringBuilder();
					builder.append(ex.id);
					builder.append(LINE_SEPARATOR);
					builder.append(ex.title);
					builder.append(LINE_SEPARATOR);
					builder.append(ex.stacktrace);
					throw new QualityCenterException(builder.toString());

				} catch (JAXBException e) {
					throw new WebApplicationException(e);
				}
			}
		}
	}

}
