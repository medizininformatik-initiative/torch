package de.medizininformatikinitiative;


import de.medizininformatikinitiative.util.model.Root;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class JsonFileReaderService {

    @Value("${json.file.path}")
    private String jsonFilePath;

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    private Root root;

    public JsonFileReaderService(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }


    public void init() throws IOException {
        loadJson();
    }

    public void loadJson() throws IOException {
        Resource resource = resourceLoader.getResource(jsonFilePath);
        root = objectMapper.readValue(resource.getInputStream(), Root.class);
    }

    public void setJsonFilePath(String jsonFilePath) throws IOException {
        this.jsonFilePath = jsonFilePath;
        loadJson();
    }

    public Root getRoot() {
        return root;
    }
}
