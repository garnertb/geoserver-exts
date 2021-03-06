package org.opengeo.mapmeter.monitor.transport;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.SimpleHttpConnectionManager;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.geotools.util.logging.Logging;
import org.opengeo.mapmeter.monitor.MapmeterRequestData;
import org.opengeo.mapmeter.monitor.config.MapmeterConfiguration;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;

public class HttpMessageTransport implements MessageTransport {

    private static final Logger LOGGER = Logging.getLogger(HttpMessageTransport.class);

    private final MapmeterConfiguration config;

    private final MessageSerializer mapmeterMessageSerializer;

    private final HttpConnectionManager connectionManager;

    private final HttpClient client;

    private static final int idleConnectionTimeout = 1000;

    public HttpMessageTransport(MapmeterConfiguration config, HttpConnectionManagerParams params) {
        if (!config.getApiKey().isPresent()) {
            LOGGER.warning("Missing mapmeter apikey. Will NOT send messages with no apikey.");
        }
        this.config = config;
        mapmeterMessageSerializer = new MessageSerializer();
        connectionManager = new SimpleHttpConnectionManager();
        connectionManager.setParams(params);
        client = new HttpClient(connectionManager);
    }

    // send request data via http post
    // if sending fails, log failure and just drop message
    @Override
    public void transport(Collection<MapmeterRequestData> data) {

        String storageUrl;
        Optional<String> maybeApiKey;
        synchronized (config) {
            storageUrl = config.getStorageUrl();
            maybeApiKey = config.getApiKey();
        }

        if (!maybeApiKey.isPresent()) {
            LOGGER.fine("Missing mapmeter apikey. NOT sending messages.");
            return;
        }

        String apiKey = maybeApiKey.get();

        PostMethod postMethod = new PostMethod(storageUrl);

        JSONObject json = mapmeterMessageSerializer.serialize(apiKey, data);
        String jsonPayload = json.toString();

        StringRequestEntity requestEntity = null;
        try {
            requestEntity = new StringRequestEntity(jsonPayload, "application/json", "UTF-8");
        } catch (UnsupportedEncodingException e) {
            Throwables.propagate(e);
        }
        postMethod.setRequestEntity(requestEntity);

        LOGGER.fine(jsonPayload);

        // send message
        try {
            int statusCode = client.executeMethod(postMethod);
            // if we receive a status code saying api key is invalid
            // we might want to signal back to the monitor filter to back off transporting messages
            // additionally, we may have a status code to signal that we should queue up messages
            // until the controller is ready to receive them again
            if (statusCode != HttpStatus.SC_OK) {
                LOGGER.severe("Error response from: " + storageUrl + " - " + statusCode);
            }
        } catch (HttpException e) {
            logCommunicationError(e, storageUrl);
        } catch (IOException e) {
            logCommunicationError(e, storageUrl);
        } finally {
            postMethod.releaseConnection();
            connectionManager.closeIdleConnections(idleConnectionTimeout);
        }
    }

    private void logCommunicationError(Exception e, String url) {
        LOGGER.log(Level.SEVERE, "Error sending messages to: " + url, e);
    }

    @Override
    public void destroy() {
    }
}
