package edu.kpi.task;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.cloud.storage.contrib.nio.CloudStorageFileSystem;
import edu.kpi.dto.DocumentRecord;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class DocumentScrapTask implements Runnable {

    private static final String BUCKET_FILE_URL_TEMPLATE = "https://storage.cloud.google.com/%s%s";

    private final CloudStorageFileSystem fileSystem;
    private final Firestore database;
    private final String baseUrl;
    private final String pageUrl;
    private final String baseFileDirectory;
    private final String collectionId;
    private final int retries;

    public DocumentScrapTask(CloudStorageFileSystem fileSystem, Firestore database, String baseUrl, String pageUrl, String baseFileDirectory, String collectionId, int retries) {
        this.fileSystem = fileSystem;
        this.database = database;
        this.baseUrl = baseUrl;
        this.pageUrl = pageUrl;
        this.baseFileDirectory = baseFileDirectory;
        this.collectionId = collectionId;
        this.retries = retries;
    }


    @Override
    public void run() {

        logRun();
        int usedAttempts = 1;
        boolean success = false;

        while (!success && usedAttempts <= retries) {

            try {

                scrapDocument();
                success = true;
                logFinished();

            } catch (Exception e) {

                usedAttempts++;
                logRetry();
            }
        }
    }

    private void scrapDocument() throws IOException {

        Document doc = Jsoup.connect(baseUrl + pageUrl).get();

        final String year = findYear(doc);

        final Optional<Element> fileElement = findFileElement(doc);
        final Optional<String> fileUrl = fileElement.map(this::findFileUrl);
        final String fileName = fileElement
                .flatMap(this::findFileName)
                .orElseGet(this::getFallbackFileName);


        final String fileDirectory = baseFileDirectory + year;
        final String filePath = fileDirectory + '/' + fileName;

        Files.createDirectories(fileSystem.getPath(fileDirectory));

        fileUrl.ifPresent(url -> {

            final ApiFuture<WriteResult> documentStoreFuture = storeDocumentRecord(doc, filePath, year);
            downloadDocument(filePath, url);
            waitForDocumentToBeStored(documentStoreFuture);
        });
    }

    private void downloadDocument(final String filePath, final String fileUrl) {

        try (
                final ReadableByteChannel readableByteChannel = Channels.newChannel(new URL(baseUrl + fileUrl).openStream());
                final FileChannel outputChannel = FileChannel.open(fileSystem.getPath(filePath), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
        ) {
            outputChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);

        } catch (IOException e) {

            System.out.println(e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private ApiFuture<WriteResult> storeDocumentRecord(final Document doc, final String filePath, final String year) {

        final DocumentReference document = database.collection(collectionId)
                .document(UUID.randomUUID().toString());

        final DocumentRecord value = DocumentRecord.builder()
                .title(findTitle(doc))
                .bucketUrl(constructBucketFileUrl(filePath))
                .originalUrl(baseUrl + pageUrl)
                .year(year)
                .authors(findAuthors(doc))
                .advisors(findAdvisors(doc))
                .keywords(findKeywords(doc))
                .collections(findCollections(doc))
                .build();

        return document.set(value);
    }

    private void waitForDocumentToBeStored(final ApiFuture<WriteResult> documentStoreFuture) {

        try {

            documentStoreFuture.get();

        } catch (InterruptedException | ExecutionException e) {

            System.out.println(e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void logRun() {

        System.out.println("Run task for " + pageUrl);
    }

    private void logFinished() {

        System.out.println("Finished task for " + pageUrl);
    }

    private void logRetry() {

        System.out.println("Retrying task for " + pageUrl);
    }

    private String findYear(Document doc) {

        return doc.select("td.metadataFieldValue.dc_date_issued").stream().findAny()
                .map(Element::childNodes)
                .map(nodes -> nodes.get(0))
                .filter(TextNode.class::isInstance)
                .map(TextNode.class::cast)
                .map(TextNode::text)
                .orElse("undefinedYear");
    }

    private Optional<Element> findFileElement(Document doc) {

        return doc.select("td[headers=t1] > a").stream()
                .findAny();
    }

    private Optional<String> findFileName(Element fileElement) {

        return Optional.ofNullable(fileElement)
                .map(Element::childNodes)
                .map(nodes -> nodes.get(0))
                .filter(TextNode.class::isInstance)
                .map(TextNode.class::cast)
                .map(TextNode::text);
    }

    private String getFallbackFileName() {

        return pageUrl.replace("/", "_");
    }

    private String findFileUrl(Element fileElement) {

        return fileElement.attr("href");
    }

    private String findTitle(Document doc) {

        return doc.select("td.metadataFieldValue.dc_title").stream().findAny()
                .map(Element::childNodes)
                .map(nodes -> nodes.get(0))
                .filter(TextNode.class::isInstance)
                .map(TextNode.class::cast)
                .map(TextNode::text)
                .orElse("undefinedTitle");
    }

    private List<String> findAuthors(Document doc) {

        return doc.select("td.metadataFieldValue.dc_contributor_author").stream().findAny()
                .map(Element::childNodes)
                .orElse(Collections.emptyList())
                .stream()
                .filter(Element.class::isInstance)
                .map(Element.class::cast)
                .filter(el -> el.hasAttr("href"))
                .map(Element::text)
                .toList();
    }

    private List<String> findAdvisors(Document doc) {

        return doc.select("td.metadataFieldValue.dc_contributor_advisor").stream().findAny()
                .map(Element::childNodes)
                .orElse(Collections.emptyList())
                .stream()
                .filter(Element.class::isInstance)
                .map(Element.class::cast)
                .map(Element::text)
                .toList();
    }

    private List<String> findKeywords(Document doc) {

        return doc.select("td.metadataFieldValue.dc_subject").stream().findAny()
                .map(Element::childNodes)
                .orElse(Collections.emptyList())
                .stream()
                .filter(TextNode.class::isInstance)
                .map(TextNode.class::cast)
                .map(TextNode::text)
                .toList();
    }

    private List<String> findCollections(Document doc) {

        return doc.select("td:contains(Appears in Collections:)").stream().findAny()
                .map(Element::siblingElements)
                .stream()
                .flatMap(Collection::stream)
                .filter(el -> el.className().equals("metadataFieldValue"))
                .findAny()
                .map(Element::childNodes)
                .orElse(Collections.emptyList())
                .stream()
                .filter(el -> el.hasAttr("href"))
                .filter(Element.class::isInstance)
                .map(Element.class::cast)
                .map(Element::text)
                .toList();
    }

    private String constructBucketFileUrl(final String filePath) {

        return BUCKET_FILE_URL_TEMPLATE.formatted(fileSystem.bucket(), filePath);
    }
}
