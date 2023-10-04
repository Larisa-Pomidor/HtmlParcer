package com.spl2;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

public class HtmlProcessor {
    private final String projectURL;
    private final String baseUri;
    private final String INPUT_HTML_PATH = "input.html";
    private final String CSS_FOLDER = "css";
    private final String OUTPUT_INDEX = "index.html";
    private final String IMG_FOLDER = "img";
    private final Document document;
    private final String[] selectorsToRemove = new String[]
            {"meta:not([name=viewport])", "script", "iframe", "[style*=display: none]",
            "noscript", "[type=hidden]", "picture source"};

    private final String[] classesToRemove = new String[]
            {"lazyloaded"};

    private final String[] attrToRemove = new String[]
            {"data-src"};

    public HtmlProcessor(String projectURL, String baseUri) {
        this.projectURL = projectURL;
        this.baseUri = baseUri;
        try {
            document = Jsoup.parse(new File(projectURL + INPUT_HTML_PATH), "UTF-8");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void parseHTML() {

        createDir(projectURL + CSS_FOLDER);
        createDir(projectURL + IMG_FOLDER);

        try {
            removeComments(document);

            for (String selector: selectorsToRemove) {
                removeElements(selector);
            }

            for (String className: classesToRemove) {
                removeClass(className);
            }

            Elements formElements = document.select("form[action]");

            for (Element formElement : formElements) {
                formElement.attr("action", "#");
            }

            for (String attr: attrToRemove) {
                removeAttr(attr);
            }

            removeAttr("data-src");

            prepareToDownload("link[rel=stylesheet]", "href", CSS_FOLDER);
            prepareToDownload("img", "src", IMG_FOLDER);

            Elements hrefLinks = document.select("[href]");
            for (Element linkElement : hrefLinks) {
                String href = linkElement.attr("href");
                if (!href.isEmpty() && !href.startsWith("css") && !href.startsWith("img")) {
                    linkElement.attr("href", "#");
                }
            }

            Elements linkElements = document.select("link[rel=stylesheet]");

            for (Element linkElement : document.select("link")) {
                if (!linkElements.contains(linkElement)) {
                    linkElement.remove();
                }
            }

            createTitleDesc();

            try (FileWriter writer = new FileWriter(projectURL + OUTPUT_INDEX)) {
                writer.write(document.outerHtml().replaceAll(baseUri, ""));
            }

            System.out.println("HTML обработан и сохранен в " + projectURL + OUTPUT_INDEX);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void prepareToDownload(String elementToDownload, String attr, String dirPath) {
        Elements linkElements = document.select(elementToDownload);
        for (Element linkElement : linkElements) {
            String href = linkElement.attr(attr);
            if (!href.isEmpty()) {
                if (!href.startsWith("http") && !href.startsWith("//")) {
                    href = baseUri + href;
                }
                if (href.startsWith("//")) {
                    href = "https:" + href;
                }
                String fileName =  new File(href).getName().replaceAll("[/?*:|\"<>]", "_");
                String cssFilePath = projectURL + dirPath + "/"
                        + fileName;
                try {
                    downloadFile(href, cssFilePath);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                linkElement.attr(attr, dirPath + "/"
                        + fileName);
            }
        }
    }

    private void createTitleDesc() {
        String newTitle = "New Page Title";
        String newDescription = "New Page Description";

        Element titleElement = document.select("title").first();
        if (titleElement == null) {
            titleElement = document.head().appendElement("title");
        }
        titleElement.text(newTitle);

        Element descriptionMeta = new Element("meta");
        descriptionMeta.attr("name", "description");
        document.head().appendChild(descriptionMeta);

        descriptionMeta.attr("content", newDescription);
    }

    private void removeClass(String className) {
        Elements lazyloadedElements = document.select("." + className);
        for (Element element : lazyloadedElements) {
            element.removeClass(className);
        }
    }

    private void removeAttr(String attr) {
        Elements selectedElements = document.select("[" +attr + "]");
        for (Element element : selectedElements) {
            element.removeAttr(attr);
        }
    }

    private void removeElements(String selector) {
        Elements selectedElements =
                document.select(selector);
        selectedElements.remove();
    }

    private void createDir(String dirPath) {
        File cssFolder = new File(dirPath);

        if (!cssFolder.exists()) {
            if (cssFolder.mkdirs()) {
                System.out.println("Папка создана: " + cssFolder.getAbsolutePath());
            } else {
                System.err.println("Не удалось создать папку: " + cssFolder.getAbsolutePath());
            }
        }
    }

    private void removeComments(Node node) {
        for (int i = 0; i < node.childNodeSize(); i++) {
            Node child = node.childNode(i);
            if (child.nodeName().equals("#comment")) {
                child.remove();
                i--;
            } else {
                removeComments(child);
            }
        }
    }

    private void downloadFile(String fileUrl, String saveToPath) throws IOException {
        URL url = new URL(fileUrl);
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
        httpConn.setRequestProperty("User-Agent", "Mozilla/5.0");
        int responseCode = httpConn.getResponseCode();


        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (InputStream inputStream = httpConn.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(saveToPath)) {
                int bytesRead;
                byte[] buffer = new byte[1024];
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
            System.out.println("Файл скачан и сохранен: " + saveToPath);
        } else {
            System.err.println("Не удалось скачать файл: " + fileUrl);
        }
        httpConn.disconnect();
    }
}
