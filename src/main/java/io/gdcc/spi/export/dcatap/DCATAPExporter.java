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
        return false;
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
        return MediaType.APPLICATION_JSON;
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
        String DCAT = "http://www.w3.org/ns/dcat#";
        model.setNsPrefix("dcat", DCAT);
        String DCT = "http://purl.org/dc/terms/";
        model.setNsPrefix("dct", DCT);
        String RDFS = "http://www.w3.org/2000/01/rdf-schema#";
        model.setNsPrefix("rdfs", RDFS);
        String DCATAP = "http://data.europa.eu/r5r/";
        model.setNsPrefix("dcatap", DCATAP);
        String VCARD = "http://www.w3.org/2006/vcard/ns#";
        model.setNsPrefix("vcard", VCARD); 
        
        
        // add the dcat ap dataset to the model
        Resource datasetModel = model.createResource("dcat:dataset");

        // not sure the next is needed, actually I do not fully understand this aspect but...
        datasetModel.addProperty(model.createProperty(RDFS, "type"), "dcat:Dataset");
        
        // dcatap:applicableLegislation <http://data.europa.eu/eli/reg/2022/868/oj>;
        datasetModel.addProperty(model.createProperty(DCATAP, "applicableLegislation"), "http://data.europa.eu/eli/reg/2022/868/oj");
        
        String persistendURL = datasetJson.getString("persistentUrl", "no-persistent-url");

        datasetModel.addProperty(model.createProperty(DCT, "identifier"), persistendURL);
        
        // drill down to some useful objects
        JsonObject datasetVersion = datasetJson.getJsonObject("datasetVersion");
        // get citation metadata block, with most important metadata
        JsonObject citationBlock = datasetVersion.getJsonObject("metadataBlocks").getJsonObject("citation");
        // get the fields array from citation block
        JsonArray citationFields = citationBlock.getJsonArray("fields");

        // absolute minimal is title and description
        String title = getPrimitiveValueFromFieldsByTypeName(citationFields, "title", "no-title");
        datasetModel.addProperty(model.createProperty(DCT, "title"), model.createLiteral(title, "en"));
        
        String description = "no-description"; 
        // that json has a complex structure :-(
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
        
        // TODO: add more metadata like creators, keywords, etc. from citationFields
        // also mandatory ones for DCAT-AP compliance
        
        //dct:provenance [ a dct:ProvenanceStatement;
        //        rdfs:label "No specific information about how the data was collected"@en;
        //];
        Resource provenanceStatment =  model.createResource()
                        .addProperty(model.createProperty(RDFS, "type"), "dct:ProvenanceStatement")
                        .addProperty(
                                model.createProperty(RDFS, "label"),
                                model.createLiteral(
                                        "No specific information about how the data was collected", "en"));
        datasetModel.addProperty(model.createProperty(DCT, "provenance"), provenanceStatment);

        // Fixed contact information for now
        // TODO: extract from datasetContact
        // example
        //dcat:contactPoint [
        //        a vcard:Kind;
        //vcard:fn "Contact point for this dataset"@en;
        //vcard:hasEmail <mailto:info@dans.knaw.nl>;
        //vcard:hasUrl <https://dans.knaw.nl>;
        //];
        Resource contactPoint = model.createResource()
                .addProperty(model.createProperty(RDFS, "type"), "vcard:Kind")
                .addProperty(
                        model.createProperty(VCARD, "fn"),
                        model.createLiteral("Contact point for this dataset", "en"))
                .addProperty(
                        model.createProperty(VCARD, "hasEmail"),
                        "mailto:info@dans.knaw.nl")
                .addProperty(
                        model.createProperty(VCARD, "hasUrl"),
                        "https://dans.knaw.nl");
        datasetModel.addProperty(model.createProperty(DCAT, "contactPoint"), contactPoint);
        
        // 
        // example
        // dct:publisher [
        //        a foaf:Agent,
        //        foaf:Organization; 
        //        foaf:name "DANS Data Station Life Sciences"@en;
        //        vcard:hasURL <http://dans.knaw.nl>;        
        //] ;
        Resource publisher = model.createResource()
                .addProperty(model.createProperty(RDFS, "type"), "foaf:Agent")
                .addProperty(model.createProperty(RDFS, "type"), "foaf:Organization")
                .addProperty(
                        model.createProperty("http://xmlns.com/foaf/0.1/name"),
                        model.createLiteral("DANS Data Station Life Sciences", "en"))
                .addProperty(
                        model.createProperty(VCARD, "hasURL"),
                        "http://dans.knaw.nl");
        datasetModel.addProperty(model.createProperty(DCT, "publisher"), publisher);
        
        // example
        // dct:creator [
        //        a foaf:Agent,
        //        foaf:Person; 
        //        foaf:name "J.N.C. Cranmer"@en;      
        //] ;
        Resource creator = model.createResource()
                .addProperty(model.createProperty(RDFS, "type"), "foaf:Agent")
                .addProperty(model.createProperty(RDFS, "type"), "foaf:Person")
                .addProperty(
                        model.createProperty("http://xmlns.com/foaf/0.1/name"),
                        model.createLiteral("J.N.C. Cranmer", "en"));
        datasetModel.addProperty(model.createProperty(DCT, "creator"), creator);
        
        
        //dct:accessRights   <http://publications.europa.eu/resource/authority/access-right/PUBLIC>;
        // need to detect if there are any access restrictions from the dataset json?
        
        // dcat:theme <http://publications.europa.eu/resource/authority/data-theme/HEAL> ;
        // probably some mapping from subjects to data-themes needed here
        // only for Health it is mandatory!
        //datasetModel.addProperty(model.createProperty(DCAT, "theme"), "http://publications.europa.eu/resource/authority/data-theme/HEAL");
        // if we have subject:'Medicine, Health and Life Sciences',	we can map to HEAL, and we could do HealthCDAT-AP
        // othetwise mapping seems useless for now.
        
        // find any files and add them as distributions
        // Note that dcat-ap there should be at least one file/distribution, 
        // but Dataverse does not force that!
        JsonArray files = datasetVersion.getJsonArray("files");
        for (int i = 0; i < files.size(); i++) {
            JsonObject fileObj = files.getJsonObject(i);
            JsonObject dataFile = fileObj.getJsonObject("dataFile");
            String fileName = dataFile.getString("filename", "no-filename");
            //String downloadUrl = dataFile.getString("downloadUrl", "no-download-url");

            // create a distribution resource
            // each one is uniquely identified by its title here
            // use string interpolation to make unique URIs
            Resource distribution = model.createResource();//"dcat:distribution/" + i)

            // if restricted, do something?
            //Boolean restricted = fileObj.getBoolean("restricted");   

            distribution.addProperty(model.createProperty(DCT, "title"), fileName);
            distribution.addProperty(model.createProperty(DCAT, "accessURL"), persistendURL);
            // we always have here DOWNLOADABLE_FILE, even if we cannot really download it
            distribution.addProperty(model.createProperty(DCT, "type"),
                    "http://publications.europa.eu/resource/authority/distribution-type/DOWNLOADABLE_FILE");
            Integer bytesize = dataFile.getInt("filesize", 0);
            distribution.addProperty(model.createProperty(DCAT, "byteSize"), bytesize.toString());

            // link the distribution to the dataset
            datasetModel.addProperty(model.createProperty(DCAT, "distribution"), distribution);
        }
        
        return model;
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
