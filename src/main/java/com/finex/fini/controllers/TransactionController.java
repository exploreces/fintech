package com.finex.fini.controllers;

import com.finex.fini.entities.Transaction;
import com.finex.fini.services.AiModelIntegrationService;
import com.finex.fini.services.PdfExtractionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    @Autowired
    private PdfExtractionService pdfExtractionService;

    @Autowired
    private AiModelIntegrationService aiModelIntegrationService;

    @GetMapping("/analyze")
    public String analyzeTransactions() {
        // Hardcoded path
        String pdfPath = "src/main/resources/gpay_statement_20251101_20251130-1-2.pdf";
        String transactions = pdfExtractionService.extractRawTextFromPdf(pdfPath);
        String result = aiModelIntegrationService.analyzeTransactions(transactions);

        return result;
    }
}
