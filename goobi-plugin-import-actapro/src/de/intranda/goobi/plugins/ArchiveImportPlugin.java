package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.goobi.production.enums.ImportReturnValue;
import org.goobi.production.enums.ImportType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.importer.DocstructElement;
import org.goobi.production.importer.ImportObject;
import org.goobi.production.importer.Record;
import org.goobi.production.plugin.interfaces.IImportPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.properties.ImportProperty;
import org.jdom2.*;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;

import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Person;
import ugh.dl.Prefs;
import ugh.exceptions.DocStructHasNoTypeException;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.TypeNotAllowedAsChildException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.exceptions.WriteException;
import ugh.fileformats.mets.MetsMods;
import de.intranda.goobi.configuration.Mapping;
import de.sub.goobi.forms.MassImportForm;
import de.sub.goobi.helper.NIOFileUtils;
import de.sub.goobi.helper.exceptions.ImportPluginException;
import lombok.extern.log4j.Log4j;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Log4j
public class ArchiveImportPlugin implements IImportPlugin, IPlugin {

    private static final Namespace h1 = Namespace.getNamespace("h1", "http://www.startext.de/HiDA/DefService/XMLSchema");

    private static XPathFactory xFactory = XPathFactory.instance();
    private static XPathExpression<Element> documentType = xFactory.compile("//h1:Block/h1:Field[@Type='Vz_AkTyp']", Filters.element(), null, h1);

    private static XPathExpression<Element> elementType = xFactory.compile("//h1:Block/h1:Field[@Type='Vz_AkTyp']", Filters.element(), null, h1);
    //    private static XPathExpression<String> stringType = xFactory.compile("//h1:Block/h1:Field[@Type='Vz_AkTyp']", Filters.fstring()(), null, h1);

    private static XPathExpression<Attribute> attributeType = xFactory.compile("//h1:Block/h1:Field[@Type='St_Titel']/@value_plain", Filters
            .attribute(), null, h1);

    private static final String PLUGIN_NAME = "intranda_ArchiveDocumentImport";

    private static final String SOURCE_FOLDER = "/opt/digiverso/goobi/import/BBFArchiv/";

    private String importFolder = "";

    private Prefs prefs;
    private MassImportForm form = null;

    private Map<String, String> docstructMap;

    private Map<String, String> metadataMap;

    private Map<String, Map<String, String>> personMap;

    private String currentIdentifier;

    //    private Record record;

    private List<Element> documentList;

    public ArchiveImportPlugin() {

        // read docstruct mapping
        docstructMap = Mapping.getInstance().getConfiguredDocstructs();

        // read metadata mapping

        metadataMap = Mapping.getInstance().getConfiguredMetadataMapping();

        personMap = Mapping.getInstance().getConfiguredPersonMapping();
    }

    @Override
    public PluginType getType() {
        return PluginType.Import;
    }

    @Override
    public String getTitle() {
        return PLUGIN_NAME;
    }

    @Override
    public void setPrefs(Prefs prefs) {
        this.prefs = prefs;

    }

    @Override
    public void setData(Record r) {
        //        record = r;
    }

    @Override
    public Fileformat convertData() throws ImportPluginException {
        Element document = documentList.get(0);
        log.info("*******************");

        Element text = ArchiveImportPlugin.documentType.evaluateFirst(document);
        String documentType = text.getAttributeValue("Value");
        log.info("document type in xml file is " + documentType);

        String rulesetType = docstructMap.get(documentType);
        currentIdentifier = document.getAttributeValue("DocKey");

        log.info("importing record " + currentIdentifier);

        log.info("ruleset type is " + rulesetType);
        MetsMods mm = null;
        try {
            mm = new MetsMods(prefs);

            DigitalDocument digitalDocument = new DigitalDocument();
            mm.setDigitalDocument(digitalDocument);
            DocStructType logicalType = prefs.getDocStrctTypeByName(rulesetType);
            DocStruct logical = digitalDocument.createDocStruct(logicalType);

            digitalDocument.setLogicalDocStruct(logical);
            DocStructType physicalType = prefs.getDocStrctTypeByName("BoundBook");
            DocStruct physical = digitalDocument.createDocStruct(physicalType);
            digitalDocument.setPhysicalDocStruct(physical);
            Metadata imagePath = new Metadata(prefs.getMetadataTypeByName("pathimagefiles"));
            imagePath.setValue("./images/");
            physical.addMetadata(imagePath);

            Metadata identifier = new Metadata(prefs.getMetadataTypeByName("CatalogIDDigital"));
            identifier.setValue(getProcessTitle());
            logical.addMetadata(identifier);

            extractMetadata(document, logical);

            if (documentList.size() > 1) {
                for (Element element : documentList) {

                    //                    Element textElement = ArchiveImportPlugin.documentType.evaluateFirst(element);
                    String identifierText = element.getAttributeValue("DocKey");
                    if (identifierText.startsWith("Vor")) {
                        //                        String docstructName = textElement.getAttributeValue("Value");
                        //                        String docstructType = docstructMap.get(docstructName);

                        DocStructType dst = prefs.getDocStrctTypeByName("Dossier");
                        DocStruct docstruct = digitalDocument.createDocStruct(dst);

                        Metadata dosierIdentifier = new Metadata(prefs.getMetadataTypeByName("CatalogIDDigital"));
                        dosierIdentifier.setValue(identifierText.replace(" ", "_"));
                        docstruct.addMetadata(dosierIdentifier);

                        logical.addChild(docstruct);

                        extractMetadata(element, docstruct);
                    }
                }
            }

        } catch (PreferencesException | MetadataTypeNotAllowedException | TypeNotAllowedForParentException | TypeNotAllowedAsChildException e) {
            throw new ImportPluginException(e);
        }

        return mm;
    }

    private void extractMetadata(Element document, DocStruct logical) {

        for (String xpath : metadataMap.keySet()) {
            attributeType = xFactory.compile(xpath, Filters.attribute(), null, h1);
            List<Attribute> attrList = attributeType.evaluate(document);
            for (Attribute attr : attrList) {

                String value = attr.getValue().replaceAll("&#x0d;", "").replaceAll("&#x0a;", "");
                MetadataType mdt = prefs.getMetadataTypeByName(metadataMap.get(xpath));
                Metadata md;
                try {
                    md = new Metadata(mdt);

                    md.setValue(value);
                    logical.addMetadata(md);
                    log.info("Importing " + mdt.getName() + " with value " + value);
                } catch (MetadataTypeNotAllowedException | DocStructHasNoTypeException e) {
                    log.error("Cannot add metadata " + mdt.getName() + " to docstruct " + logical.getType().getName());
                }
            }
        }

        for (String xpath : personMap.keySet()) {
            elementType = xFactory.compile(xpath, Filters.element(), null, h1);
            List<Element> list = elementType.evaluate(document);
            for (Element element : list) {
                Map<String, String> personConfiguration = personMap.get(xpath);

                String metadataType = personConfiguration.get(Mapping.METADATA);
                String lastnameValue = null;
                String firstnameValue = null;
                String gndValue = null;

                String lastnameXpath = personConfiguration.get(Mapping.LASTNAME);
                attributeType = xFactory.compile(lastnameXpath, Filters.attribute(), null, h1);
                List<Attribute> attrList = attributeType.evaluate(element);
                for (Attribute attr : attrList) {
                    lastnameValue = attr.getValue().replaceAll("&#x0d;", "").replaceAll("&#x0a;", "");
                }

                String firstnameXpath = personConfiguration.get(Mapping.FIRSTNAME);
                if (StringUtils.isNotBlank(firstnameXpath)) {
                    attributeType = xFactory.compile(firstnameXpath, Filters.attribute(), null, h1);
                    attrList = attributeType.evaluate(element);
                    for (Attribute attr : attrList) {
                        firstnameValue = attr.getValue().replaceAll("&#x0d;", "").replaceAll("&#x0a;", "");
                    }
                }

                String identifierXpath = personConfiguration.get(Mapping.IDENTIFIER);
                if (StringUtils.isNotBlank(identifierXpath)) {
                    attributeType = xFactory.compile(identifierXpath, Filters.attribute(), null, h1);
                    attrList = attributeType.evaluate(element);
                    for (Attribute attr : attrList) {
                        gndValue = attr.getValue().replaceAll("&#x0d;", "").replaceAll("&#x0a;", "");
                    }
                }

                log.info("Importing person " + metadataType + " with lastname " + lastnameValue);

                try {
                    Person person = new Person(prefs.getMetadataTypeByName(metadataType));
                    person.setLastname(lastnameValue);
                    if (firstnameValue != null) {
                        person.setFirstname(firstnameValue);
                    }
                    if (gndValue != null) {
                        person.setAutorityFile("GND", "http://d-nb.info/gnd/", gndValue);
                    }
                    logical.addPerson(person);
                } catch (MetadataTypeNotAllowedException e) {
                    log.error(e);
                }
            }

        }
        // statische Metadaten
        try {
            Metadata archiveName = new Metadata(prefs.getMetadataTypeByName("ArchiveName"));
            archiveName.setValue("Archiv der Bibliothek für Bildungsgeschichtliche Forschung (BBF) des Deutschen Instituts für Internationale Paedagogische Forschung (DIPF)");
            logical.addMetadata(archiveName);

            Metadata archiveAbbreviation = new Metadata(prefs.getMetadataTypeByName("ArchiveAbbreviation"));
            archiveAbbreviation.setValue("DIPF/BBF/Archiv");
            logical.addMetadata(archiveAbbreviation);

            Metadata collection = new Metadata(prefs.getMetadataTypeByName("singleDigCollection"));
            collection.setValue("Georg-Herwegh-Oberschule#Prüfungen");
            logical.addMetadata(collection);
        } catch (MetadataTypeNotAllowedException | DocStructHasNoTypeException e) {
            log.error(e);
        }
    }

    @Override
    public String getImportFolder() {
        return importFolder;
    }

    @Override
    public String getProcessTitle() {
        return currentIdentifier.replaceAll(" ", "_");
    }

    @Override
    public List<ImportObject> generateFiles(List<Record> records) {
        List<ImportObject> convertedList = new LinkedList<>();

        for (Record record : records) {
            if (form != null) {
                form.addProcessToProgressBar();
            }

            SAXBuilder builder = new SAXBuilder();
            try {
                Document root = builder.build(new File(record.getData()));
                Element documentSet = root.getRootElement();

                if (documentSet.getName().equals("Document")) {
                    this.documentList = new ArrayList<>();
                    documentList.add(documentSet);
                    ImportObject io = runConversion();
                    convertedList.add(io);
                } else {
                    List<Element> documentList = documentSet.getChildren("Document", h1);
                    this.documentList = documentList;

                    ImportObject io = runConversion();
                    convertedList.add(io);

                }
            } catch (JDOMException | IOException e) {
                log.error(e);
            }

        }

        return convertedList;
    }

    public ImportObject runConversion() {

        ImportObject io = new ImportObject();

        try {
            Fileformat ff = convertData();
            String fileName = getImportFolder() + File.separator + getProcessTitle() + ".xml";

            ff.write(fileName);

            io.setProcessTitle(getProcessTitle());
            io.setMetsFilename(fileName);
            io.setImportReturnValue(ImportReturnValue.ExportFinished);

        } catch (ImportPluginException | WriteException | PreferencesException e) {
            log.error(e);
        }
        return io;

    }

    @Override
    public void setForm(MassImportForm form) {
        this.form = form;

    }

    @Override
    public void setImportFolder(String folder) {
        importFolder = folder;
    }

    @Override
    public List<Record> splitRecords(String records) {
        return null;
    }

    @Override
    public List<Record> generateRecordsFromFile() {
        return null;
    }

    @Override
    public List<Record> generateRecordsFromFilenames(List<String> selectedFilenames) {
        List<Record> recordList = new LinkedList<>();
        for (String filename : selectedFilenames) {

            Record record = new Record();
            record.setId("filename");
            record.setData(SOURCE_FOLDER + filename);
            recordList.add(record);
        }
        return recordList;
    }

    @Override
    public void setFile(File importFile) {
    }

    @Override
    public List<String> splitIds(String ids) {
        return null;
    }

    @Override
    public List<ImportType> getImportTypes() {
        List<ImportType> typeList = new ArrayList<>(1);
        typeList.add(ImportType.FOLDER);
        return typeList;
    }

    @Override
    public List<ImportProperty> getProperties() {
        return null;
    }

    @Override
    public List<String> getAllFilenames() {

        return NIOFileUtils.list(SOURCE_FOLDER);
    }

    @Override
    public void deleteFiles(List<String> selectedFilenames) {
        // TODO Auto-generated method stub
    }

    @Override
    public List<? extends DocstructElement> getCurrentDocStructs() {
        return null;
    }

    @Override
    public String deleteDocstruct() {
        return null;
    }

    @Override
    public String addDocstruct() {
        return null;
    }

    @Override
    public List<String> getPossibleDocstructs() {
        return null;
    }

    @Override
    public DocstructElement getDocstruct() {
        return null;
    }

    @Override
    public void setDocstruct(DocstructElement dse) {
    }

    public String getDescription() {
        return null;
    }

}
