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
        String baseUri = "https://www.shopdisney.com/";

        String projectURL = test ? "src/main/resources/" : "";

        HtmlProcessor htmlProcessor = new HtmlProcessor(projectURL, baseUri);
        htmlProcessor.parseHTML();

    }
}
