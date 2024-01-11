package com.spl2;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        boolean test = true;
        boolean includeJs = true;
        String baseUri = "https://www.topessaywriting.org/";

        String projectURL = test ? "src/main/resources/" : "";

        HtmlProcessor htmlProcessor = new HtmlProcessor(projectURL, baseUri, includeJs);
        htmlProcessor.parseHTML();

    }
}
