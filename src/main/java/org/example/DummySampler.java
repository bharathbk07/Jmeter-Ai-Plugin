package org.example;

import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DummySampler extends AbstractSampler {

    private static final Logger LOG = LoggerFactory.getLogger(DummySampler.class);

    private static final String RESPONSE_TIME_PROPERTY = "Dummy.responseTime";
    private static final String LABEL = "Dummy.label";
    private static final String RESPONSE_CODE = "Dummy.responseCode";
    private static final String SUCCESS = "Dummy.Success";

    private static final String MODEL = "AI_MODEL";
    private static final String TOKEN = "AI_TOKEN";
    private static final String TEMPERATURE = "AI_TEMPERATURE";
    private static final String MAX_TOKENS = "AI_MAX_TOKENS";
    private static final String REQUEST = "AI_REQUEST";

    public void setResponseTime(int responseTime) {
        setProperty(RESPONSE_TIME_PROPERTY, responseTime);
    }

    public Integer getResponseTime() {
        return getPropertyAsInt(RESPONSE_TIME_PROPERTY, 1000);
    }

    public void setLabel(String label) {
        setProperty(LABEL, label);
    }

    public String getLabel() {
        return getPropertyAsString(LABEL, Strings.EMPTY);
    }

    public void setResponseCode(String responseCode) {
        setProperty(RESPONSE_CODE, responseCode);
    }

    public String getResponseCode() {
        return getPropertyAsString(RESPONSE_CODE, "200");
    }

    public void setSuccessful(boolean success) {
        setProperty(SUCCESS, success);
    }

    public boolean getSuccessful() {
        return getPropertyAsBoolean(SUCCESS, true);
    }

    public String getModel() {
        return getPropertyAsString(MODEL);
    }

    public void setModel(String model) {
        setProperty(MODEL, model);
    }

    public String getToken() {
        return getPropertyAsString(TOKEN);
    }

    public void setToken(String token) {
        setProperty(TOKEN, token);
    }

    public String getTemperature() {
        return getPropertyAsString(TEMPERATURE);
    }

    public void setTemperature(String temp) {
        setProperty(TEMPERATURE, temp);
    }

    public String getMaxTokens() {
        return getPropertyAsString(MAX_TOKENS);
    }

    public void setMaxTokens(String tokens) {
        setProperty(MAX_TOKENS, tokens);
    }

    public String getRequest() {
        return getPropertyAsString(REQUEST);
    }

    public void setRequest(String request) {
        setProperty(REQUEST, request);
    }

    public SampleResult sample(Entry entry) {
        SampleResult result = new SampleResult();
        result.setSampleLabel(getLabel());
        result.setResponseCode(getResponseCode());
        result.setSuccessful(getSuccessful());
        result.sampleStart();
        try {
            Thread.sleep(getResponseTime());
        } catch (InterruptedException e) {
            result.setSuccessful(false);
            result.setResponseMessage(e.getMessage());
            LOG.error("Error while sleep", e);
            Thread.currentThread().interrupt();
        }
        result.sampleEnd();
        return result;
    }

}
