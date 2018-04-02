package de.jlo.talendcomp.camunda.externaltask;

import java.io.UnsupportedEncodingException;
import java.net.URL;

import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

public class HttpClient {
	
	private static Logger LOG = LoggerFactory.getLogger(HttpClient.class);
	private int connectionTimeout = 1000;
	private int statusCode = 0;
	private String statusMessage = null;
	private int maxRetriesInCaseOfErrors = 0;
	private int currentAttempt = 0;
	private long waitMillisAfterError = 1000l;
	
	public String get(String urlStr, String user, String password) throws Exception {
        CloseableHttpClient httpclient = createClient(urlStr, user, password);
        try {
            HttpGet request = new HttpGet(urlStr);
            CloseableHttpResponse response = httpclient.execute(request);
            try {
            	statusCode = response.getStatusLine().getStatusCode();
            	statusMessage = response.getStatusLine().getReasonPhrase();
                return EntityUtils.toString(response.getEntity());
            } finally {
                response.close();
            }
        } finally {
            httpclient.close();
        }
	}
	
	private HttpEntity buildEntity(String content, String encoding) throws UnsupportedEncodingException {
		if (content != null && content.trim().isEmpty() == false) {
			HttpEntity entity = new StringEntity(content);
			return entity;
		} else {
			return null;
		}
	}
	
	private HttpEntity buildEntity(JsonNode node) throws UnsupportedEncodingException {
		if (node != null && node.isNull() == false && node.isMissingNode() == false) {
			HttpEntity entity = new StringEntity(node.toString());
			return entity;
		} else {
			return null;
		}
	}

	private String execute(CloseableHttpClient httpclient, HttpPost request, boolean expectResponse) throws Exception {
		for (currentAttempt = 0; currentAttempt <= maxRetriesInCaseOfErrors; currentAttempt++) {
            CloseableHttpResponse response = null;
            try {
            	response = httpclient.execute(request);
            	statusCode = response.getStatusLine().getStatusCode();
            	statusMessage = response.getStatusLine().getReasonPhrase();
            	if (expectResponse) {
                	String responseContent = EntityUtils.toString(response.getEntity());
                	if (Util.isEmpty(responseContent)) {
                		throw new Exception("Empty response received.");
                	}
                    return responseContent;
            	} else {
            		return "";
            	}
//            } catch (NoRouteToHostException e) {
//            	LOG.error("POST request: " + request.getURI() + " failed.", e);
//            	throw new Exception("POST request: " + request.getURI() + " failed.", e);
//            } catch (UnknownHostException e) {
//            	LOG.error("POST request: " + request.getURI() + " failed.", e);
//            	throw new Exception("POST request: " + request.getURI() + " failed.", e);
            } catch (Exception e) {
            	if (currentAttempt <= maxRetriesInCaseOfErrors) {
                	// this can happen, we try it again
                	LOG.warn("POST request: " + request.getURI() + " failed (" + (currentAttempt + 1) + ". attempt). Waiting " + waitMillisAfterError + "ms and retry request.", e);
                	Thread.sleep(waitMillisAfterError);
            	} else {
                	LOG.error("POST request: " + request.getURI() + " failed.", e);
                	throw new Exception("POST request: " + request.getURI() + " failed.", e);
            	}
            } finally {
            	if (response != null) {
                    response.close();
            	}
            }
		}
    	httpclient.close();
		return null;
	}

	public String post(String urlStr, String user, String password, JsonNode node, boolean expectResponse) throws Exception {
        CloseableHttpClient httpclient = createClient(urlStr, user, password); 
        HttpPost request = new HttpPost(urlStr);
        request.getConfig();
        if (node != null) {
            request.setEntity(buildEntity(node));
            request.addHeader("Accept", "application/json");
            request.addHeader("Content-Type", "application/json;charset=UTF-8");
        }
        return execute(httpclient, request, expectResponse);
	}

	public String post(String urlStr, String user, String password, String content, String encoding, boolean expectResponse) throws Exception {
        CloseableHttpClient httpclient = createClient(urlStr, user, password);
        HttpPost request = new HttpPost(urlStr);
        request.getConfig();
        if (content != null) {
            request.setEntity(buildEntity(content, encoding));
            request.addHeader("Content-Type", "application/json;charset=UTF-8");
            request.addHeader("Accept", "application/json");
        }
        return execute(httpclient, request, expectResponse);
	}
	
	private CloseableHttpClient createClient(String urlStr, String user, String password) throws Exception {
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        if (user != null && user.trim().isEmpty() == false) {
    		URL url = new URL(urlStr);
            credsProvider.setCredentials(
                    new AuthScope(url.getHost(), url.getPort()),
                    new UsernamePasswordCredentials(user, password));
            RequestConfig requestConfig = RequestConfig.custom()
                    .setSocketTimeout(connectionTimeout)
                    .setConnectTimeout(connectionTimeout)
                    .setConnectionRequestTimeout(connectionTimeout)
                    .setRedirectsEnabled(true)
                    .setRelativeRedirectsAllowed(true)
                    .build();
            CloseableHttpClient client = HttpClients.custom()
                    .setDefaultCredentialsProvider(credsProvider)
                    .setDefaultRequestConfig(requestConfig)
                    .build();
            return client;
        } else {
            return HttpClients.custom()
                    .build();
        }
	}

	public int getTimeout() {
		return connectionTimeout;
	}

	public void setTimeout(Integer timeout) {
		if (timeout != null) {
			this.connectionTimeout = timeout;
		}
	}

	public void setTimeoutInSec(Integer timeout) {
		if (timeout != null) {
			this.connectionTimeout = (timeout * 1000);
		}
	}

	public int getStatusCode() {
		return statusCode;
	}

	public String getStatusMessage() {
		return statusMessage;
	}

	public int getConnectionTimeout() {
		return connectionTimeout;
	}

	public void setConnectionTimeout(Integer connectionTimeout) {
		if (connectionTimeout != null) {
			this.connectionTimeout = connectionTimeout;
		}
	}

	public int getMaxRetriesInCaseOfErrors() {
		return maxRetriesInCaseOfErrors;
	}

	public void setMaxRetriesInCaseOfErrors(Integer maxRetriesInCaseOfErrors) {
		if (maxRetriesInCaseOfErrors != null) {
			this.maxRetriesInCaseOfErrors = maxRetriesInCaseOfErrors;
		}
	}

	public int getCurrentAttempt() {
		return currentAttempt;
	}

	public long getWaitMillisAfterError() {
		return waitMillisAfterError;
	}

	public void setWaitMillisAfterError(Long waitMillisAfterError) {
		if (waitMillisAfterError != null) {
			this.waitMillisAfterError = waitMillisAfterError;
		}
	}

}
