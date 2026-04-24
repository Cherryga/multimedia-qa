package com.assignment.multimediaqa.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

@Service
public class PdfExtractorService {

    public String extractText(String filePath) {
        try (var document = Loader.loadPDF(new File(filePath))) {
            return new PDFTextStripper().getText(document).trim();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
