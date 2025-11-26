package io.gdcc.spi.export.dcatap;

import com.google.auto.service.AutoService;
import io.gdcc.spi.export.ExportDataProvider;
import io.gdcc.spi.export.ExportException;
import io.gdcc.spi.export.Exporter;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.MediaType;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.impl.RDFWriterFImpl;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFFormatVariant;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFWriter;
import org.apache.jena.riot.writer.RDFJSONWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
// Maybe use vocabs, but for now just use strings which I like more
//import org.apache.jena.vocabulary.RDFS;

@AutoService(Exporter.class)
public class DCATAPExporter implements Exporter {
    static String DCAT = "http://www.w3.org/ns/dcat#";
    static String DCT = "http://purl.org/dc/terms/";
    static String RDFS = "http://www.w3.org/2000/01/rdf-schema#";
    static String DCATAP = "http://data.europa.eu/r5r/";
    static String VCARD = "http://www.w3.org/2006/vcard/ns#";
    static String FOAF = "http://xmlns.com/foaf/0.1/";
    static String SPDX = "http://spdx.org/rdf/terms#";
    
    
    /**
     * The name of the format it creates. If this format is already provided by a built-in exporter,
     * this Exporter will override the built-in one. (Note that exports are cached, so existing
     * metadata export files are not updated immediately.)
     */
    @Override
    public String getFormatName() {
        return "dcatap";
    }

    /**
     * The display name shown in the UI.
     *
     * @param locale Used to generate a translation.
     */
    @Override
    public String getDisplayName(Locale locale) {
        return "DCAT-AP";
    }

    /** Whether the exported format should be available as an option for Harvesting. */
    @Override
    public Boolean isHarvestable() {
        //return false;
        return true; // For the RDF XML we can have it harvestable
    }

    /** Whether the exported format should be available for download in the UI and API. */
    @Override
    public Boolean isAvailableToUsers() {
        return true;
    }

    /**
     * Defines the mime type of the exported format. Used when metadata is downloaded, i.e. to
     * trigger an appropriate viewer in the user's browser.
     */
    @Override
    public String getMediaType() {
        //return MediaType.APPLICATION_JSON;
        return MediaType.APPLICATION_XML; // RDF/XML as default for DCAT-AP, somehow JSON-LD does not work well
        // also we would like XML in the OAI-PMH harvesting output!
    }
    
    /**
     * This method is called by Dataverse when metadata for a given dataset in this format is
     * requested.
     */
    @Override
    public void exportDataset(ExportDataProvider dataProvider, OutputStream outputStream)
            throws ExportException {
        try {
            JsonObject datasetJson = dataProvider.getDatasetJson();
            Model model = createRDFModelFromDatasetJson(datasetJson);
            
            // TODO: how could we support these different output types using this same exporter?
            model.write(outputStream); // THIS always works, defaults to RDF/XML
 
            // Try some others
            //RDFJSONWriter.output(outputStream, model.getGraph()); // works, but no context!
            //model.write(outputStream, "RDF/XML"); // alos works, but thaht was the default anyway
            //model.write(outputStream, "TURTLE"); // org.apache.jena.shared.NoWriterForLangException: Writer not found: TURTLE
            // at org.apache.jena.rdf.model.impl.RDFWriterFImpl.getWriter(RDFWriterFImpl.java:66)
            
            //RDFDataMgr.write(outputStream, model, RDFFormat.TURTLE);
            // here I got: 
            // Caused by: java.lang.NoSuchMethodError: 'java.lang.String org.apache.jena.atlas.lib.Lib.lowercase(java.lang.String)'
            //        at org.apache.jena.riot.RDFLanguages.canonicalKey(RDFLanguages.java:470)
            
            //RDFWriter.source(model.getGraph()).format(RDFFormat.JSONLD).output(outputStream);            
            //RDFDataMgr.write(outputStream, model, RDFFormat.JSONLD);
            //RDFDataMgr.write(outputStream, model, Lang.JSONLD11);
            
            // These work at build time, but fail when the jar is deployed as plugin in Dataverse:
            //model.write(outputStream, "JSON-LD"); // other exporters use this too
            //model.write(outputStream, "RDF/XML"); // when for OAI harvesting, XML fits best
            //model.write(outputStream, "TURTLE"); // human-readable text format, best for debugging
            
            // Flush the output stream - The output stream is automatically closed by
            // Dataverse and should not be closed in the Exporter.
            outputStream.flush();
        } catch (Exception ex) {
            System.out.println("Exception caught in DCAT-AP exporter. Printing stacktrace...");
            ex.printStackTrace();
            // If anything goes wrong, an Exporter should throw an ExportException.
            throw new ExportException("Unknown exception caught during export: " + ex);
        }
    }
    
    Model createRDFModelFromDatasetJson(JsonObject datasetJson) {
        Model model = ModelFactory.createDefaultModel();
        // The RDF stuff using Apache Jena

        // make the model use prefixes
        model.setNsPrefix("dcat", DCAT);
        model.setNsPrefix("dct", DCT);
        model.setNsPrefix("rdfs", RDFS);
        model.setNsPrefix("dcatap", DCATAP);
        model.setNsPrefix("vcard", VCARD);
        model.setNsPrefix("foaf", FOAF);
        model.setNsPrefix("spdx", SPDX);
        
        // add the dcat ap dataset to the model
        Resource datasetModel = model.createResource("dcat:dataset");

        // not sure the next is needed, actually I do not fully understand this aspect but...
        datasetModel.addProperty(model.createProperty(RDFS, "type"), "dcat:Dataset");
        
        // applicableLegislation, only mandatory for HealthDCAT-AP, so skipping for now
        // dcatap:applicableLegislation <http://data.europa.eu/eli/reg/2022/868/oj>;
        //datasetModel.addProperty(model.createProperty(DCATAP, "applicableLegislation"), "http://data.europa.eu/eli/reg/2022/868/oj");
        
        String persistendURL = datasetJson.getString("persistentUrl", "no-persistent-url");

        datasetModel.addProperty(model.createProperty(DCT, "identifier"), persistendURL);
        
        // drill down to some useful objects
        JsonObject datasetVersion = datasetJson.getJsonObject("datasetVersion");
        
        // add version info
        // note that the citation uses a 'V' before the version number, 
        // so we do that here as well
        int versionNumber = datasetVersion.getInt("versionNumber");
        int versionMinorNumber = datasetVersion.getInt("versionMinorNumber");
        String versionInfo = String.format("V%d.%d", versionNumber, versionMinorNumber);
        datasetModel.addProperty(model.createProperty(DCT, "hasVersion"), versionInfo);
        
        // get citation metadata block, with most important metadata
        JsonObject citationBlock = datasetVersion.getJsonObject("metadataBlocks").getJsonObject("citation");
        // get the fields array from citation block
        JsonArray citationFields = citationBlock.getJsonArray("fields");

        // absolute minimal for DCAT is title and description
        
        String title = getPrimitiveValueFromFieldsByTypeName(citationFields, "title", "no-title");
        datasetModel.addProperty(model.createProperty(DCT, "title"), model.createLiteral(title, "en"));
        
        String description = "no-description"; // provide default, because mandatory 
        JsonArray dsDescriptions = getValuesFromCompoundFieldByTypeName(citationFields, "dsDescription");
        // find the first dsDescriptionValue
        for (int i = 0; i < dsDescriptions.size(); i++) {
            JsonObject dsDescription = dsDescriptions.getJsonObject(i);
            JsonObject dsDescriptionValue = dsDescription.getJsonObject("dsDescriptionValue");
            if (dsDescriptionValue != null) {
                description = dsDescriptionValue.getString("value", "no-description");
                break;
            }
        }
        datasetModel.addProperty(model.createProperty(DCT, "description"), model.createLiteral(description, "en"));
        
        // add more metadata from citationFields
        
        String pubDate = datasetVersion.getString("publicationDate", "no-publication-date");
        datasetModel.addProperty(model.createProperty(DCT, "issued"), pubDate);
        // lastUpdateTime is actually the same if the Dataset is published, but...
        String lastUpdateTime = datasetVersion.getString("lastUpdateTime", "no-last-update-time");
        // parse lastUpdateTime to proper date object
        String formattedLastUpdateTime = lastUpdateTime;
        try {
            LocalDateTime parsedDateTime = LocalDateTime.parse(lastUpdateTime, DateTimeFormatter.ISO_DATE_TIME);
            formattedLastUpdateTime = parsedDateTime.format(DateTimeFormatter.ISO_DATE);
        } catch (Exception e) {
            System.out.println("Failed to parse lastUpdateTime: " + lastUpdateTime);
        }
        datasetModel.addProperty(model.createProperty(DCT, "modified"), formattedLastUpdateTime);
        
        // next also mandatory ones for DCAT-AP compliance
        
        // we do not have any explicit provenance information in Dataverse for now
        Resource provenanceStatment =  model.createResource()
                        .addProperty(model.createProperty(RDFS, "type"), "dct:ProvenanceStatement")
                        .addProperty(
                                model.createProperty(RDFS, "label"),
                                model.createLiteral(
                                        "No specific information about how the data was collected", "en"));
        datasetModel.addProperty(model.createProperty(DCT, "provenance"), provenanceStatment);
        
        // Publisher would be the organisation behind the Dataverse installation itself
        // maybe we can make the plugin configurable to set this properly?
        // This is mandatory for DCAT-AP-NL, not for DCAT-AP, so skipping for now
//        Resource publisher = model.createResource()
//                .addProperty(model.createProperty(RDFS, "type"), "foaf:Agent")
//                .addProperty(model.createProperty(RDFS, "type"), "foaf:Organization")
//                .addProperty(
//                        model.createProperty("http://xmlns.com/foaf/0.1/name"),
//                        model.createLiteral("DANS Data Station Life Sciences", "en"))
//                .addProperty(
//                        model.createProperty(VCARD, "hasURL"),
//                        "http://dans.knaw.nl");
//        datasetModel.addProperty(model.createProperty(DCT, "publisher"), publisher);

        JsonArray contactPoints = getValuesFromCompoundFieldByTypeName(citationFields, "datasetContact");
        Resource contactPoint = createContactPoint(model, contactPoints);
        datasetModel.addProperty(model.createProperty(DCAT, "contactPoint"), contactPoint);

        // Creators are the authors from citationFields
        JsonArray authors = getValuesFromCompoundFieldByTypeName(citationFields, "author");
        for (int i = 0; i < authors.size(); i++) {
            JsonObject authorObj = authors.getJsonObject(i);
            Resource creatorResource = createCreator(model, authorObj);
            datasetModel.addProperty(model.createProperty(DCT, "creator"), creatorResource);
        }

        // alternative title, is alternativeTitle multiple primitive
        List<String> altTitles = getPrimitiveValueArrayFromFieldsByTypeName(citationFields, "alternativeTitle");
        for (String altTitleValue : altTitles) {
                datasetModel.addProperty(
                        model.createProperty(DCT, "alternative"),
                        model.createLiteral(altTitleValue, "en"));
        }
        
        // Note: we could try to determine dataset license, and somehow add it to each file distribution as well
        // we have the name and the uri in the license object, but can we map that?
        // just the URI, but note that we do nat have license on dataset level, only on file level
//        JsonObject licenseObj = datasetVersion.getJsonObject("license");
//        if (licenseObj != null) {
//            String licenseURI = licenseObj.getString("uri", "");
//            if (!licenseURI.isEmpty()) {
//                datasetModel.addProperty(model.createProperty(DCT, "license"), licenseURI);
//            }
//        }
        
        // Determine the dataset language, if any
        // Note that it is not the same as the metadata language, which is now assumed default '@en' !
        // also there is a multi-values fields, we can have multiple languages and the list of code is huge
        // and most like need mapping to something useful for DCAT-AP
        //String language = getPrimitiveValueFromFieldsByTypeName(citationFields, "language", "");
        
        // accessRights is mandatory for DCAT-AP-NL, not for DCAT-AP, so skipping for now
        //dct:accessRights   <http://publications.europa.eu/resource/authority/access-right/PUBLIC>;
        // need to detect if there are any access restrictions from the dataset json?
        
        // dcat:theme <http://publications.europa.eu/resource/authority/data-theme/HEAL> ;
        // probably some mapping from subjects to data-themes needed here
        // only for Health it is mandatory!
        //datasetModel.addProperty(model.createProperty(DCAT, "theme"), "http://publications.europa.eu/resource/authority/data-theme/HEAL");
        // if we have subject:'Medicine, Health and Life Sciences',	we can map to HEAL, and we could do HealthCDAT-AP
        // otherwise mapping seems useless for now.

        // keywords would be good, if we have them
        JsonArray keywords = getValuesFromCompoundFieldByTypeName(citationFields, "keyword");
        for (int i = 0; i < keywords.size(); i++) {
            JsonObject keywordObj = keywords.getJsonObject(i);
            JsonObject keywordValueObj = keywordObj.getJsonObject("keywordValue");
            if (keywordValueObj != null) {
                String keywordValue = keywordValueObj.getString("value", "");
                // Note that keywords kan have URI's when an CVOC is used, but for now just use the literal value
                if (!keywordValue.isEmpty()) {
                    datasetModel.addProperty(
                            model.createProperty(DCAT, "keyword"),
                            model.createLiteral(keywordValue, "en"));
                }
            }
        }
        // subjects as dct:subject, could also be added as a keyword
        // however, what if it just was 'Other', we could skip that as a keyword I think ?
        
        // find any files and add them as distributions
        // Note that dcat-ap there should be at least one file/distribution, 
        // but Dataverse does not force that!
        JsonArray files = datasetVersion.getJsonArray("files");
        for (int i = 0; i < files.size(); i++) {
            JsonObject fileObj = files.getJsonObject(i);
            Resource distribution = createFileDistribution(model, fileObj);
            // add the accessURL to the distribution, using the dataset persistent URL
            distribution.addProperty(model.createProperty(DCAT, "accessURL"), persistendURL);
            // link the distribution to the dataset
            datasetModel.addProperty(model.createProperty(DCAT, "distribution"), distribution);
        }
        
        return model;
    }
    
    Resource createFileDistribution(Model model, JsonObject fileObj) {
        Resource distribution = model.createResource();

        JsonObject dataFile = fileObj.getJsonObject("dataFile");
        String fileName = dataFile.getString("filename", "no-filename");
        //String downloadUrl = dataFile.getString("downloadUrl", "no-download-url");
        // accessURL is added later, when we have the dataset persistent URL, 
        // there is no download URL for a file alone
        
        // if restricted, do something?
        //Boolean restricted = fileObj.getBoolean("restricted");   

        distribution.addProperty(model.createProperty(DCT, "title"), fileName);
        
        // type is not mandatory for DCAT-AP, so skipping for now
        // we always have here DOWNLOADABLE_FILE, even if we cannot really download it?
        //distribution.addProperty(model.createProperty(DCT, "type"),
        //        "http://publications.europa.eu/resource/authority/distribution-type/DOWNLOADABLE_FILE");
        
        Integer bytesize = dataFile.getInt("filesize", 0);
        distribution.addProperty(model.createProperty(DCAT, "byteSize"), bytesize.toString());
        
        // description is mandatory for DCAT-AP, so provide a default
        // dct:description "No specific description available"@en;
        String description = dataFile.getString("description", "No specific description available");
        if (description.isEmpty()) {
            description = "No specific description available";
        }
        distribution.addProperty(
                model.createProperty(DCT, "description"),
                model.createLiteral(description, "en"));
        
        
        // DCT format; use MIME type from contentType, but that would need some mapping
        // use mediatype from IANA
        String mimeType = dataFile.getString("contentType", "application/octet-stream");
        distribution.addProperty(
                model.createProperty(DCAT, "mediaType"),
                "http://www.iana.org/assignments/media-types/" + mimeType);
        
        
        // checksum would be nice to have as well
        JsonObject checksumObj = dataFile.getJsonObject("checksum");
        if (checksumObj != null) {
            String checksumValue = checksumObj.getString("value", "");
            String checksumType = checksumObj.getString("type", "MD5"); // default to MD5
            String checksumAlgorithm = getSPDXChecksumAlgorithmURI(checksumType);
            if (!checksumAlgorithm.isEmpty() && !checksumValue.isEmpty()) {
                Resource checksumResource = model.createResource()
                        .addProperty(
                                model.createProperty(SPDX, "algorithm"),
                                checksumAlgorithm)
                        .addProperty(
                                model.createProperty(SPDX, "checksumValue"),
                                checksumValue);
                distribution.addProperty(model.createProperty(SPDX, "checksum"), checksumResource);
            }
        }
        
        return distribution;
    }
    
    // return the algorithm for SPDX based on the type string from Dataverse
    String getSPDXChecksumAlgorithmURI(String type) {
        // note that Dataverse StandardSupportedAlgorithms use a minus with SHA
        switch (type.toUpperCase()) {
            case "MD5":
                return "checksumAlgorithm_md5";
            case "SHA-1":
                return "checksumAlgorithm_sha1";
            case "SHA-224": 
                return "checksumAlgorithm_sha224";
            case "SHA-256":
                return "checksumAlgorithm_sha256";
            case "SHA-512":
                return "checksumAlgorithm_sha512";
            default:
                return ""; // empty indicates we do not have a mapping
        }
    }
    
    Resource createCreator(Model model, JsonObject author) {
        Resource creatorResource = model.createResource();
            //creatorResource.addProperty(model.createProperty(RDFS, "type"), "foaf:Agent");
            // assume person for now
        creatorResource.addProperty(model.createProperty(RDFS, "type"), "foaf:Person");
        
        JsonObject authorName = author.getJsonObject("authorName");
        if (authorName != null) {
            String authorNameValue = authorName.getString("value", "");
            // But what if it is a ORCID or other autor identifier?
            creatorResource.addProperty(
                    model.createProperty(FOAF, "name"),
                    model.createLiteral(authorNameValue, "en"));
        }
        JsonObject authorAffiliation = author.getJsonObject("authorAffiliation");
        if (authorAffiliation != null) {
            String authorAffiliationValue = authorAffiliation.getString("value", "");
            if (!authorAffiliationValue.isEmpty()) {
                // vcard for affiliation, supposed to be organization-name
                // But what if it is a ROR or other organisation identifier?
                // expandedvalue.termName could be used for getting the humanreadable name
                creatorResource.addProperty(
                        model.createProperty(VCARD, "organization-name"),
                        model.createLiteral(authorAffiliationValue, "en"));
            }
        }
        return creatorResource;
    }
    
    Resource createContactPoint (Model model, JsonArray contactPoints) {
        Resource contactPointResource = null;
        // just take the first one for now, if any
        
        if (contactPoints.size() > 0) {
            contactPointResource = model.createResource();
            
            JsonObject contactPointObj = contactPoints.getJsonObject(0);
            JsonObject contactName = contactPointObj.getJsonObject("datasetContactName");
            if (contactName != null) {
                String contactNameValue = contactName.getString("value", "");
                // But what if it is a ORCID or other autor identifier?
                contactPointResource.addProperty(
                        model.createProperty(VCARD, "fn"),
                        model.createLiteral(contactNameValue, "en"));
            }

            JsonObject contactEmail = contactPointObj.getJsonObject("datasetContactEmail");
            if (contactEmail != null) {
                String contactEmailValue = contactEmail.getString("value", "");
                if (!contactEmailValue.isEmpty()) {
                    contactPointResource.addProperty(
                            model.createProperty(VCARD, "hasEmail"),
                            "mailto:" + contactEmailValue);
                }
            }
            
            JsonObject contactAffiliation = contactPointObj.getJsonObject("datasetContactAffiliation");
            if (contactAffiliation != null) {
                String contactAffiliationValue = contactAffiliation.getString("value", "");
                if (!contactAffiliationValue.isEmpty()) {
                    // vcard for affiliation, supposed to be organization-name
                    contactPointResource.addProperty(
                            model.createProperty(VCARD, "organization-name"),
                            model.createLiteral(contactAffiliationValue, "en"));
                }
            }
        }
        
        return contactPointResource;
    }
    
    // JSON helper stuff, should be able to get better Dataverse JSON aware stuff from elsewhere?
    
    String getPrimitiveValueFromFieldsByTypeName(JsonArray fields, String typeName, String defaultValue) {
        for (int i = 0; i < fields.size(); i++) {
            JsonObject field = fields.getJsonObject(i);
            if (field.getString("typeName").equals(typeName)) {
                // if it has "typeClass": "primitive", just get the value
                if (field.getString("typeClass").equals("primitive")) {
                    return field.getString("value", defaultValue);
                }
            }
        }
        return defaultValue;
    }
    
    // we can have a primitive with multiple values as well, so return a String array
    List<String> getPrimitiveValueArrayFromFieldsByTypeName(JsonArray fields, String typeName)
    {
        for (int i = 0; i < fields.size(); i++) {
            JsonObject field = fields.getJsonObject(i);
            if (field.getString("typeName").equals(typeName)) {
                // if it has "typeClass": "primitive", just get the value
                if (field.getString("typeClass").equals("primitive")) {
                    // check that we have "multiple": true
                    if (!field.getBoolean("multiple", false)) {
                        // not multiple, list with single value
                        String singleValue = field.getString("value", "");
                        return java.util.Collections.singletonList(singleValue);
                    } else {
                        JsonArray valuesArray = field.getJsonArray("value");
                        // convert JsonArray to List<String>
                        List<String> valuesList = new java.util.ArrayList<>();
                        for (int j = 0; j < valuesArray.size(); j++) {
                            valuesList.add(valuesArray.getString(j, ""));
                        }
                        return valuesList;
                    }
                }
            }
        }
        return java.util.Collections.emptyList();
    }
    
    
    JsonArray getValuesFromCompoundFieldByTypeName(JsonArray fields, String typeName) {
        for (int i = 0; i < fields.size(); i++) {
            JsonObject field = fields.getJsonObject(i);
            if (field.getString("typeName").equals(typeName)) {
                // if it has "typeClass": "compound", get the value array
                if (field.getString("typeClass").equals("compound")) {
                    return field.getJsonArray("value");
                }
            }
        }
        // return an empty JsonArray
        return Json.createArrayBuilder().build();
    }
}
