package org.scm4j.ai;

import java.io.InputStream;
import java.net.URL;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.HttpClientBuilder;

public class DefaultSource implements ISource {

	@Override
	public InputStream getContent(URL url) {
		try {
			HttpClient client = HttpClientBuilder.create().build();
			HttpRequestBase req = new HttpGet(url.toURI());
			HttpResponse resp = client.execute(req);
			
			return resp.getEntity().getContent();
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}
}
