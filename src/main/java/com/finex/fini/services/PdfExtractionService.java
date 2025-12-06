package com.finex.fini.services;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class PdfExtractionService {

    private static final Logger log = LoggerFactory.getLogger(PdfExtractionService.class);

    public String extractRawTextFromPdf(String pdfPath) {
        log.info("Starting RAW PDF extraction for file: {}", pdfPath);

        try (PDDocument document = PDDocument.load(new File(pdfPath))) {

            log.debug("PDF loaded successfully");

            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            log.info("PDF raw text extracted ({} characters)", text.length());
            log.debug("Extracted PDF Text:\n{}", text);

            return text;

        } catch (Exception e) {
            log.error("Failed to extract text from PDF: {}", pdfPath, e);
            return ""; // Return empty string so AI can still get something
        }
    }
}
