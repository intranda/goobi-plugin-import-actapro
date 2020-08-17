package de.intranda.goobi.configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;

import lombok.extern.log4j.Log4j;

@Log4j
public class Mapping {

    private static final String configurationFile = "/opt/digiverso/goobi/config/config_actaproMetadataMapping.xml";

    private static Mapping instance;
    private static XMLConfiguration config;

    public static final String FIRSTNAME = "firstname";
    public static final String LASTNAME = "lastname";
    public static final String METADATA = "metadata";
    public static final String IDENTIFIER = "identifier";

    public static synchronized Mapping getInstance() {
        if (instance == null) {
            instance = new Mapping();
        }
        return instance;
    }

    private Mapping() {
        config = getConfiguration();
    }

    private static XMLConfiguration getConfiguration() {
        XMLConfiguration config;
        try {
            config = new XMLConfiguration(configurationFile);
        } catch (ConfigurationException e) {
            log.error(e);
            config = new XMLConfiguration();
        }
        config.setReloadingStrategy(new FileChangedReloadingStrategy());
        config.setExpressionEngine(new XPathExpressionEngine());
        return config;
    }

    public Map<String, String> getConfiguredDocstructs() {
        Map<String, String> docstructMap = new HashMap<>();

        List<HierarchicalConfiguration> docstructList = config.configurationsAt("docstructs/docstruct");
        for (HierarchicalConfiguration docstruct : docstructList) {
            String actaproName = docstruct.getString("@actapro");
            String rulesetName = docstruct.getString("@ruleset");
            docstructMap.put(actaproName, rulesetName);
        }
        return docstructMap;
    }

    public Map<String, String> getConfiguredMetadataMapping() {
        Map<String, String> metadataMap = new HashMap<>();

        List<HierarchicalConfiguration> docstructList = config.configurationsAt("metadata/element");
        for (HierarchicalConfiguration docstruct : docstructList) {
            String xpath = docstruct.getString("@xpath");
            String metadataName = docstruct.getString("@metadata");
            metadataMap.put(xpath, metadataName);
        }
        return metadataMap;
    }

    public Map<String, Map<String, String>> getConfiguredPersonMapping() {
        Map<String, Map<String, String>> allPersons = new HashMap<>();

        List<HierarchicalConfiguration> personElementList = config.configurationsAt("person/element");
        for (HierarchicalConfiguration personElement : personElementList) {
            String xpath = personElement.getString("@xpath");

            Map<String, String> currentPerson = new HashMap<>();
            String firstname = personElement.getString("firstname/@xpath");
            String lastname = personElement.getString("lastname/@xpath");
            String identifier = personElement.getString("identifier/@xpath");
            String metadata = personElement.getString("@metadata");

            currentPerson.put(FIRSTNAME, firstname);
            currentPerson.put(LASTNAME, lastname);
            currentPerson.put(IDENTIFIER, identifier);
            currentPerson.put(METADATA, metadata);

            allPersons.put(xpath, currentPerson);
        }
        return allPersons;
    }

    public Map<String, Map<String, String>> getConfiguredConcordanceTables() {
        Map<String, Map<String, String>> allTables = new HashMap<>();

        List<HierarchicalConfiguration> concordanceElementList = config.configurationsAt("concordance");
        for (HierarchicalConfiguration concordanceElement : concordanceElementList) {
            String metadataName = concordanceElement.getString("@metadata");
            List<HierarchicalConfiguration> valueList = concordanceElement.configurationsAt("value");
            Map<String, String> values = new HashMap<>();
            for (HierarchicalConfiguration val : valueList) {
                values.put(val.getString("@for"), val.getString("@val"));
            }
            allTables.put(metadataName, values);
        }

        return allTables;
    }

}
