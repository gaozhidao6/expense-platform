package com.shixi.expense.invoice.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.shixi.expense.invoice.dto.InvoiceRecognitionResult;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class MockInvoiceRecognitionClientTests {

    private final MockInvoiceRecognitionClient client =
            new MockInvoiceRecognitionClient();

    @Test
    void returnsStructuredSuggestionWithoutExternalService() throws Exception {
        InvoiceRecognitionResult result = client.recognize(
                new ByteArrayInputStream(new byte[] {1, 2, 3}),
                "application/pdf");

        assertNotNull(result.invoiceNumber());
        assertNotNull(result.invoiceDate());
        assertNotNull(result.amount());
        assertNotNull(result.merchantName());
        assertNotNull(result.confidence());
        assertEquals("MOCK-20260914-001", result.invoiceNumber());
    }

    @Test
    void rejectsMissingInputStream() {
        assertThrows(
                IllegalArgumentException.class,
                () -> client.recognize(null, "application/pdf"));
    }

    @Test
    void rejectsBlankContentType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> client.recognize(
                        new ByteArrayInputStream(new byte[] {1}),
                        " "));
    }
}
