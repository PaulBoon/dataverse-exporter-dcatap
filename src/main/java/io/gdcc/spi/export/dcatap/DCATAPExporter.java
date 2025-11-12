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
//            JsonObject datasetORE = dataProvider.getDatasetORE();
//            JsonObject datasetSchemaDotOrg = dataProvider.getDatasetSchemaDotOrg();
//            JsonArray datasetFileDetails = dataProvider.getDatasetFileDetails();
//            String dataCiteXml = dataProvider.getDataCiteXml();
//
//            JsonObjectBuilder job = Json.createObjectBuilder();
//            job.add("datasetJson", datasetJson);
//            job.add("datasetORE", datasetORE);
//            job.add("datasetSchemaDotOrg", datasetSchemaDotOrg);
//            job.add("datasetFileDetails", datasetFileDetails);
//            job.add("dataCiteXml", dataCiteXml);
//
//            // Write the output format to the output stream.
//            outputStream.write(job.build().toString().getBytes(StandardCharsets.UTF_8));
            
            // get citation metadata block
            JsonObject datasetVersion = datasetJson.getJsonObject("datasetVersion");

            JsonObject citationBlock = datasetVersion.getJsonObject("metadataBlocks").getJsonObject("citation");
            // get the fields array
            JsonArray citationFields = citationBlock.getJsonArray("fields");
            
            // absolute minimal is title and descriptiopn
            String title = getPrimitiveValueFromFieldsByTypeName(citationFields, "title", "No Title");
            String description = getPrimitiveValueFromFieldsByTypeName(citationFields, "description", "No Description");
            
            // that json has a complex structure :-(
            JsonArray dsDescriptions = getValuesFromCompoundFieldByTypeName(citationFields, "dsDescription");
            // find the first dsDescriptionValue
            for (int i = 0; i < dsDescriptions.size(); i++) {
                JsonObject dsDescription = dsDescriptions.getJsonObject(i);
                JsonObject dsDescriptionValue = dsDescription.getJsonObject("dsDescriptionValue");
                if (dsDescriptionValue != null) {
                    description = dsDescriptionValue.getString("value", "No Description");
                    break;
                }
            }
            
            // RDF stuff using Apache Jena
            
            // create an empty Model
            Model model = ModelFactory.createDefaultModel();
            // make the model use prefixes
            model.setNsPrefix("dcat", "http://www.w3.org/ns/dcat#");
            model.setNsPrefix("dcterms", "http://purl.org/dc/terms/");
            model.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
            
            // add the dcat ap dataset to the model
            Resource datasetModel = model.createResource("dcat:dataset")
                    .addProperty(model.createProperty("dcterms:title"), title)
                    .addProperty(model.createProperty("dcterms:description"), description);

            // not sure the next is needed, but...
            datasetModel.addProperty(model.createProperty("rdfs:type"), "dcat:Dataset");
            
            // find any files and add them as distributions
            JsonArray files = datasetVersion.getJsonArray("files");
            for (int i = 0; i < files.size(); i++) {
                JsonObject fileObj = files.getJsonObject(i);
                JsonObject dataFile = fileObj.getJsonObject("dataFile");
                String fileName = dataFile.getString("filename", "no-filename");
                //String downloadUrl = dataFile.getString("downloadUrl", "no-download-url");
                
                // create a distribution resource
                // each one is uniquely identified by its title here
                // use string interpolation to make unique URIs
                Resource distribution = model.createResource("dcat:distribution/" + i)
                        .addProperty(model.createProperty("dcterms:title"), fileName);
                        //.addProperty(model.createProperty("dcat:downloadURL"), downloadUrl);
                
                // link the distribution to the dataset
                datasetModel.addProperty(model.createProperty("dcat:distribution"), model.createResource("dcat:distribution/" + i)
                        .addProperty(model.createProperty("dcterms:title"), fileName));
            }
            
            model.write(outputStream, "JSON-LD");
            //model.write(outputStream, "RDF/XML");
            //model.write(outputStream, "TURTLE");
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
    
    // json helper stuff
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
