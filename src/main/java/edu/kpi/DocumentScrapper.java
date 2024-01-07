package edu.kpi;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.storage.contrib.nio.CloudStorageConfiguration;
import com.google.cloud.storage.contrib.nio.CloudStorageFileSystem;
import edu.kpi.task.DocumentScrapTask;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DocumentScrapper {

    private static final String SEARCH_BY_DATE = "/browse?type=dateissued";

    private final String baseUrl;
    private final String baseFileDirectory;
    private final String bucketName;
    private final String projectId;
    private final String databaseId;
    private final String collectionId;
    private final int retries;

    public DocumentScrapper(String baseUrl, String baseFileDirectory, String bucketName, String projectId, String databaseId, String collectionId, int retries) {

        this.baseUrl = baseUrl;
        this.baseFileDirectory = baseFileDirectory;
        this.bucketName = bucketName;
        this.projectId = projectId;
        this.databaseId = databaseId;
        this.collectionId = collectionId;
        this.retries = retries;
    }

    public void downloadDocuments() throws IOException, ExecutionException, InterruptedException {

        ExecutorService executorService = Executors.newFixedThreadPool(4);

        final CloudStorageFileSystem fileSystem = getFilesystem();
        final Firestore database = getDatabase();

        Optional<String> nextPageLink = Optional.of(SEARCH_BY_DATE);

        while (nextPageLink.isPresent()) {

            List<Future<?>> futures = new ArrayList<>();

            Document doc = Jsoup.connect(baseUrl + nextPageLink.get()).get();

            doc.select("td[headers=t2] > a")
                    .stream()
                    .map(el -> new DocumentScrapTask(fileSystem, database, baseUrl, el.attr("href"), baseFileDirectory, collectionId, retries))
                    .map(executorService::submit)
                    .forEach(futures::add);

            nextPageLink = doc.select("a.pull-right").stream()
                    .findAny()
                    .map(el -> el.attr("href"));

            for (Future<?> future : futures) {

                future.get();
            }
        }

        executorService.shutdown();
    }

    private Firestore getDatabase() {

        return FirestoreOptions.getDefaultInstance().toBuilder()
                .setProjectId(projectId)
                .setDatabaseId(databaseId)
                .build()
                .getService();
    }

    private CloudStorageFileSystem getFilesystem() {

        return CloudStorageFileSystem.forBucket(
                bucketName,
                CloudStorageConfiguration.DEFAULT,
                StorageOptions.newBuilder()
                        .build());
    }
}