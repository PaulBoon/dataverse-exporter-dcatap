package io.gdcc.spi.export.dcatap;

import static org.junit.jupiter.api.Assertions.*;

import io.gdcc.spi.export.ExportDataProvider;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

public class DCATAPExporterTest {

    static DCATAPExporter exporter;
    static OutputStream outputStream;
    static ExportDataProvider dataProvider;

    @BeforeAll
    public static void setUp() {
        exporter = new DCATAPExporter();
        outputStream = new ByteArrayOutputStream();
        dataProvider =
                new ExportDataProvider() {
                    @Override
                    public JsonObject getDatasetJson() {
                        String pathToJsonFile = "src/test/resources/cars/in/datasetJson.json";
                        try (JsonReader jsonReader =
                                Json.createReader(new FileReader(pathToJsonFile))) {
                            return jsonReader.readObject();
                        } catch (FileNotFoundException ex) {
                            return null;
                        }
                    }

                    @Override
                    public JsonObject getDatasetORE() {
                        String pathToJsonFile = "src/test/resources/cars/in/datasetORE.json";
                        try (JsonReader jsonReader =
                                Json.createReader(new FileReader(pathToJsonFile))) {
                            return jsonReader.readObject();
                        } catch (FileNotFoundException ex) {
                            return null;
                        }
                    }

                    @Override
                    public JsonArray getDatasetFileDetails() {
                        String pathToJsonFile =
                                "src/test/resources/cars/in/datasetFileDetails.json";
                        try (JsonReader jsonReader =
                                Json.createReader(new FileReader(pathToJsonFile))) {
                            return jsonReader.readArray();
                        } catch (FileNotFoundException ex) {
                            return null;
                        }
                    }

                    @Override
                    public JsonObject getDatasetSchemaDotOrg() {
                        String pathToJsonFile =
                                "src/test/resources/cars/in/datasetSchemaDotOrg.json";
                        try (JsonReader jsonReader =
                                Json.createReader(new FileReader(pathToJsonFile))) {
                            return jsonReader.readObject();
                        } catch (FileNotFoundException ex) {
                            return null;
                        }
                    }

                    @Override
                    public String getDataCiteXml() {
                        try {
                            return Files.readString(
                                    Paths.get("src/test/resources/cars/in/dataCiteXml.xml"),
                                    StandardCharsets.UTF_8);
                        } catch (IOException ex) {
                            return null;
                        }
                    }
                };
    }

    @Test
    public void testGetFormatName() {
        DCATAPExporter instance = new DCATAPExporter();
        String expResult = "";
        String result = instance.getFormatName();
        assertEquals("dcatap", result);
    }

    @Test
    public void testGetDisplayName() {
        assertEquals("DCAT-AP", exporter.getDisplayName(null));
    }

    @Test
    public void testIsHarvestable() {
        assertEquals(false, exporter.isHarvestable());
    }

    @Test
    public void testIsAvailableToUsers() {
        assertEquals(true, exporter.isAvailableToUsers());
    }

    @Test
    public void testGetMediaType() {
        assertEquals("application/json", exporter.getMediaType());
    }

    @Test
    public void testExportDataset() throws Exception {
        exporter.exportDataset(dataProvider, outputStream);
//        String expected =
//                Files.readString(
//                        Paths.get("src/test/resources/cars/expected/expected.json"),
//                        StandardCharsets.UTF_8);
        String actual = outputStream.toString();
        writeFile(actual, "cars");
//        JSONAssert.assertEquals(expected, actual, true);
//        assertEquals(prettyPrint(expected), prettyPrint(outputStream.toString()));
        // print actual, for inital testing!
        System.out.println("Actual DCAT-AP output:");
        System.out.println(prettyPrint(actual));
    }

    private void writeFile(String actual, String name) throws IOException {
        Path dir = Files.createDirectories(Paths.get("src/test/resources/" + name + "/out"));
        Path out = Paths.get(dir + "/croissant.json");
        Files.writeString(out, prettyPrint(actual), StandardCharsets.UTF_8);
    }

    public static String prettyPrint(String jsonObject) {
        try {
            return prettyPrint(getJsonObject(jsonObject));
        } catch (Exception ex) {
            return jsonObject;
        }
    }

    public static String prettyPrint(JsonObject jsonObject) {
        Map<String, Boolean> config = new HashMap<>();
        config.put(JsonGenerator.PRETTY_PRINTING, true);
        JsonWriterFactory jsonWriterFactory = Json.createWriterFactory(config);
        StringWriter stringWriter = new StringWriter();
        try (JsonWriter jsonWriter = jsonWriterFactory.createWriter(stringWriter)) {
            jsonWriter.writeObject(jsonObject);
        }
        return stringWriter.toString();
    }

    public static JsonObject getJsonObject(String serializedJson) {
        try (StringReader rdr = new StringReader(serializedJson)) {
            try (JsonReader jsonReader = Json.createReader(rdr)) {
                return jsonReader.readObject();
            }
        }
    }
}
