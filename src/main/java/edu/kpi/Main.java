package edu.kpi;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Main {

    private static final String BASE_URL = "https://ela.kpi.ua/";
    private static final String BASE_FILE_DIRECTORY = "/home/rd/Documents/training/llm/scraper/document-scraper/files/";

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {

        ExecutorService executorService = Executors.newFixedThreadPool(4);

        String searchByDate = "/browse?type=dateissued";

        Optional<String> nextPageLink = Optional.of(searchByDate);

        List<Future<?>> futures = new ArrayList<>();

        while (nextPageLink.isPresent()) {

            Document doc = Jsoup.connect(BASE_URL + nextPageLink.get()).get();

            doc.select("td[headers=t2] > a")
                    .stream()
                    .map(el -> executorService.submit(() -> downloadDocument(el.attr("href"))))
                    .forEach(futures::add);

            nextPageLink = doc.select("a.pull-right").stream()
                    .findAny()
                    .map(el -> el.attr("href"));
        }

        for (Future<?> future : futures) {

            future.get();
        }

        executorService.close();
    }

    private static void downloadDocument(final String pageUrl) {

        System.out.println("Submit task for " + pageUrl);

        try {

            Document doc = Jsoup.connect(BASE_URL + pageUrl).get();

            final String year = doc.select("td.metadataFieldValue.dc_date_issued").stream().findAny()
                    .map(Element::childNodes)
                    .map(nodes -> nodes.get(0))
                    .filter(TextNode.class::isInstance)
                    .map(TextNode.class::cast)
                    .map(TextNode::text)
                    .orElse("undefinedYear");

            final Optional<Element> fileElement = doc.select("td[headers=t1] > a").stream()
                    .findAny();

            final Optional<String> fileUrl = fileElement.map(el -> el.attr("href"));
            final String fileName = fileElement.map(Element::childNodes)
                    .map(nodes -> nodes.get(0))
                    .filter(TextNode.class::isInstance)
                    .map(TextNode.class::cast)
                    .map(TextNode::text)
                    .orElse(pageUrl.replace("/", "_"));

            final String fileDirectory = BASE_FILE_DIRECTORY + year;
            final String filePath = fileDirectory + "/" + fileName;

            Files.createDirectories(Paths.get(fileDirectory));

            fileUrl.ifPresent(url -> {

                try (ReadableByteChannel readableByteChannel = Channels.newChannel(new URL(BASE_URL + url).openStream());

                     FileOutputStream fileOutputStream = new FileOutputStream(filePath)
                ) {

                    fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });


        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        System.out.println("finished " + pageUrl);
    }
}