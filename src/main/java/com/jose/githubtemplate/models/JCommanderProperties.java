package com.jose.githubtemplate.models;

import com.beust.jcommander.Parameter;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

public @Data class JCommanderProperties {
    @Parameter(names = {"-s", "-source"}, description = "Source URL")
    private String source;

    @Parameter(names = {"-f", "-file"}, description = "Template file")
    private List<String> files = new ArrayList<>();
}
