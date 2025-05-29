package org.example.gui;

import javax.swing.*;
import java.awt.*;
import org.apache.jmeter.samplers.gui.AbstractSamplerGui;
import org.apache.jmeter.testelement.TestElement;
import org.example.DummySampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DummySamplerGui extends AbstractSamplerGui {

    private static final Logger LOG = LoggerFactory.getLogger(DummySamplerGui.class);
    private JTextArea requestArea;
    private JTextArea responseArea;
    private JTextField modelField;
    private JTextField tokenField;
    private JTextField tempField;
    private JTextField maxTokensField;

    public DummySamplerGui() {
        setLayout(new BorderLayout());
        setBorder(makeBorder());
        add(makeTitlePanel(), BorderLayout.NORTH);
        add(createMainPanel(), BorderLayout.CENTER);
        LOG.info("DummySamplerGui initialized");
    }

    private JPanel createMainPanel() {
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.add("API Configuration", createConfigPanel());
        tabbedPane.add("Request/Response", createRequestResponsePanel());
        return wrapInPanel(tabbedPane);
    }

    private JPanel createConfigPanel() {
        JPanel configPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 2, 2, 2);

        modelField = new JTextField(20);
        tokenField = new JTextField(20);
        tempField = new JTextField("0.7", 20);
        maxTokensField = new JTextField("100", 20);

        addToPanel(configPanel, "Model:", modelField, c, 0);
        addToPanel(configPanel, "API Token:", tokenField, c, 1);
        addToPanel(configPanel, "Temperature:", tempField, c, 2);
        addToPanel(configPanel, "Max Tokens:", maxTokensField, c, 3);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(configPanel, BorderLayout.NORTH);
        return wrapper;
    }

    private JPanel createRequestResponsePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1.0;
        c.weighty = 1.0;
        c.insets = new Insets(2, 2, 2, 2);

        requestArea = new JTextArea();
        responseArea = new JTextArea();
        requestArea.setRows(10);
        responseArea.setRows(10);
        responseArea.setEditable(false);

        c.gridx = 0;
        c.gridy = 0;
        panel.add(new JLabel("Request:"), c);
        c.gridy = 1;
        panel.add(new JScrollPane(requestArea), c);
        c.gridy = 2;
        panel.add(new JLabel("Response:"), c);
        c.gridy = 3;
        panel.add(new JScrollPane(responseArea), c);

        return panel;
    }

    private void addToPanel(JPanel panel, String label, JComponent field, GridBagConstraints c, int gridy) {
        c.gridx = 0;
        c.gridy = gridy;
        c.weightx = 0;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        panel.add(field, c);
    }

    private JPanel wrapInPanel(Component component) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(component, BorderLayout.CENTER);
        return wrapper;
    }

    @Override
    public String getLabelResource() {
        return "dummy_sampler_title";
    }

    @Override
    public String getStaticLabel() {
        return "JMeter AI Assistant";
    }

    @Override
    public TestElement createTestElement() {
        DummySampler sampler = new DummySampler();
        configureTestElement(sampler);
        return sampler;
    }

    @Override
    public void modifyTestElement(TestElement element) {
        configureTestElement(element);
        if (element instanceof DummySampler) {
            DummySampler sampler = (DummySampler) element;
            sampler.setModel(modelField.getText());
            sampler.setToken(tokenField.getText());
            sampler.setTemperature(tempField.getText());
            sampler.setMaxTokens(maxTokensField.getText());
            sampler.setRequest(requestArea.getText());
        }
    }

    @Override
    public void configure(TestElement element) {
        super.configure(element);
        if (element instanceof DummySampler) {
            DummySampler sampler = (DummySampler) element;
            modelField.setText(sampler.getModel());
            tokenField.setText(sampler.getToken());
            tempField.setText(sampler.getTemperature());
            maxTokensField.setText(sampler.getMaxTokens());
            requestArea.setText(sampler.getRequest());
        }
    }
}
