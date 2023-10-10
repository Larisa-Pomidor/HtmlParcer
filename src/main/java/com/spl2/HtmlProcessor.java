package com.spl2;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
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
            {"meta:not([name=viewport])", "script", "iframe", "[style*=display: none]", "video",
            "noscript", "[type=hidden]", "picture source", "link[rel=preconnect]", "link[rel=preload]"};

    private final String[] classesToRemove = new String[]
            {"lazyloaded"};

    private final String[] attrToRemove = new String[]
            {"data-src", "srcset", "target"};

    private static final String[] FONT_STYLES = {
            "font-family: Arial, sans-serif",
            "font-family: Times New Roman, serif;",
            "font-family: Helvetica, sans-serif;",
            "font-family: Georgia, serif;",
            "font-family: Verdana, sans-serif;",
            "font-family: Courier New, monospace;",
            "font-family: Palatino Linotype, Book Antiqua, Palatino, serif;",
            "font-family: Tahoma, Geneva, sans-serif;",
            "font-family: Impact, Charcoal, sans-serif;",
            "font-family: Lucida Sans Unicode, Lucida Grande, sans-serif;",
    };

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
            removeFontFaceFromStyles();

            String fontStyles = generateFontStyles();
            document.head().append(fontStyles);

            manageMenu();

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
            prepareToDownloadBackgroundImagesInStyle("*",  IMG_FOLDER);

            Elements hrefLinks = document.select("[href]");
            for (Element linkElement : hrefLinks) {
                String href = linkElement.attr("href");
                if (!href.isEmpty() && !href.startsWith("css") && !href.startsWith("img")) {
                    linkElement.attr("href", "#");
                }
            }

            // ?

            Elements allElements = document.getAllElements();
            for (Element element : allElements) {
                Attributes attributes = element.attributes();
                for (Attribute attribute : attributes) {
                    if (attribute.getValue().matches(".*https?://.*")) {
                        element.removeAttr(attribute.getKey());
                    }
                }
            }

            // ?

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
                if (isGoogleFontsLink(href)) {
                    continue;
                }

                String fileNameWithParams = new File(href).getName();
                String fileName = fileNameWithParams.split("\\?")[0];

                String cssFilePath = projectURL + dirPath + "/"
                        + fileName;
                try {
                    downloadFile(href, cssFilePath);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                linkElement.attr(attr, dirPath + "/"
                        + fileName);
                linkElement.removeAttr("id");
            }
        }
    }

    private void prepareToDownloadBackgroundImagesInStyle(String elementSelector, String dirPath) {
        Elements elementsWithStyle = document.select(elementSelector);

        for (Element element : elementsWithStyle) {
            String style = element.attr("style");
            if (style != null && style.matches(".*background\\s*:\\s*url.*")) {
                String imageUrl = extractImageUrlFromStyle(style);

                if (imageUrl != null && !imageUrl.isEmpty()) {
                    String fileName = getFileNameFromUrl(imageUrl);
                    String imagePath = projectURL + dirPath + "/" + fileName;

                    try {
                        downloadFile(imageUrl, imagePath);

                        element.attr("style", "background:url(" + dirPath + "/" + fileName + ")");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    private String extractImageUrlFromStyle(String style) {
        String imageUrl = null;

        style = style.replaceAll("&quot;", "\\'");
        String regex = "background: *url\\(['\"]?([^'\"\\)]+)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(style);

        while (matcher.find()) {
            imageUrl = matcher.group(1);
        }

        return imageUrl;
    }

    private String getFileNameFromUrl(String imageUrl) {
        String[] parts = imageUrl.split("/");
        return parts[parts.length - 1].split("\\?")[0];
    }

    private boolean isGoogleFontsLink(String href) {
        return href.contains("fonts.googleapis.com");
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

    private void removeFontFaceFromStyles() {
        Elements styleElements = document.select("style");

        for (Element styleElement : styleElements) {
            String style = styleElement.html();

            style = style.replaceAll("@font-face[^}]*\\}", "");

            styleElement.html(style);
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

    private String generateFontStyles() {
        StringBuilder css = new StringBuilder("<style>\n");
        css.append("h1, h2, h3, h4, h5, h6, p, a, span, div, strong, em, i, b, u, s, " +
                "strike, sub, sup, blockquote, code, pre, li, ol, ul, label, button, input, " +
                "textarea, select, option, th, td, caption, small, caption, article, aside, figure, " +
                "footer, header, nav, section, main{\n");

        Random random = new Random();
        int randomIndex = random.nextInt(FONT_STYLES.length);
        String randomFont = FONT_STYLES[randomIndex];

        css.append(randomFont).append("!important;");
        css.append("}\n");
        css.append("</style>");

        return css.toString();
    }

    private void manageMenu() {

        StringBuilder js = new StringBuilder("<script>\n");
        js.append("let menu_button = document.querySelector(\".menu_button\")\n");
        js.append("let menu = document.querySelector(\".menu_switch\")\n");
        js.append("menu_button.onclick = function () { \n");
        js.append("menu.classList.contains(\"active\") ? \n");
        js.append("menu.classList.remove(\"active\") : \n");
        js.append("menu.classList.add(\"active\"); \n");
        js.append("} \n");
        js.append("</script>");

        document.head().append(js.toString());

        StringBuilder css = new StringBuilder("<style>\n");
        css.append("@media(max-width: 600px) {\n");
        css.append(".menu_switch.active {\n");
        css.append("}\n");
        css.append("}\n");
        css.append("</style>");

        document.head().append(css.toString());
    }
}
