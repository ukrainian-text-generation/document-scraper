package edu.kpi;

public class Main {

    public static void main(String[] args) throws Exception {

        String baseUrl = args[0];
        String baseFileDirectory = args[1];
        String bucketName = args[2];
        String projectId = args[3];
        String databaseId = args[4];
        String collectionId = args[5];
        int retries = Integer.parseInt(args[6]);

        new DocumentScrapper(baseUrl, baseFileDirectory, bucketName, projectId, databaseId, collectionId, retries)
                .downloadDocuments();
    }
}
