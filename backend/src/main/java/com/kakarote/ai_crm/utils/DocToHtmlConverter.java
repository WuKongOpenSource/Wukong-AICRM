package com.kakarote.ai_crm.utils;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToXMLContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

public final class DocToHtmlConverter {

    private DocToHtmlConverter() {
    }

    public static String convertToHtml(InputStream inputStream) throws IOException, SAXException, TikaException {
        AutoDetectParser parser = new AutoDetectParser();
        ToXMLContentHandler handler = new ToXMLContentHandler();
        parser.parse(inputStream, handler, new Metadata(), new ParseContext());
        return handler.toString();
    }
}
