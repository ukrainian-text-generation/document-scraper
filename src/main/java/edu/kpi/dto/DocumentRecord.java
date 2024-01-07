package edu.kpi.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DocumentRecord {

    private String title;
    private String bucketUrl;
    private String originalUrl;
    private String year;
    private List<String> authors;
    private List<String> advisors;
    private List<String> keywords;
    private List<String> collections;
}
