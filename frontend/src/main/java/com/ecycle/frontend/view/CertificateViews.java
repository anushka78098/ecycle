package com.ecycle.frontend.view;

import com.ecycle.frontend.api.ApiClient;
import com.ecycle.frontend.component.*;
import com.ecycle.frontend.util.Alerts;
import com.ecycle.frontend.util.Async;
import com.ecycle.frontend.util.J;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.FileChooser;

/** Verifiable recycling / data-destruction / data-sanitization certificates: list, download the PDF, share the verification link, check any certificate. */
public final class CertificateViews {
    private CertificateViews() {}

    public static ResourceView list() {
        ResourceView v = new ResourceView("Certificates", "PDF certificates issued after verification. Each carries a verification code and a QR code to the public check page.", () -> ApiClient.get("/api/certificates"));
        v.col("Number", "certificateNo").col("Type", n -> J.human(J.s(n, "type"))).col("Issued to", "holderName").col("Issued by", "issuedByName").col("Asset", "passportCode").date("Issued", "issuedAt").col("Verification code", "verificationCode")
                .action("Download PDF", n -> true, n -> download(J.s(n, "certificateNo")))
                .action("Copy verification link", n -> true, n -> { ClipboardContent c = new ClipboardContent(); c.putString(J.s(n, "verifyUrl")); Clipboard.getSystemClipboard().setContent(c); Alerts.info("Verification link copied."); })
                .action("Details", n -> true, n -> details(n)).onOpen(CertificateViews::details)
                .tool("Verify a certificate...", CertificateViews::verifyDialog);
        return v;
    }

    private static void download(String no) {
        FileChooser fc = new FileChooser(); fc.setInitialFileName(no + ".pdf"); fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File f = fc.showSaveDialog(null); if (f == null) return;
        Async.run(() -> { byte[] b = ApiClient.bytes("/api/certificates/" + no + "/pdf"); Files.write(f.toPath(), b); return f; }, x -> Alerts.info("Saved to " + x.getAbsolutePath()));
    }

    private static void details(JsonNode n) {
        StringBuilder sb = new StringBuilder(); n.path("details").fields().forEachRemaining(e -> sb.append(J.human(e.getKey())).append(": ").append(e.getValue().asText()).append('\n'));
        TextArea t = new TextArea(sb + "\nContent hash (SHA-256): " + J.s(n, "contentHash") + (J.s(n, "custodyHeadHash").isBlank() ? "" : "\nChain-of-custody anchor: entry #" + J.l(n, "custodySeq") + " " + J.s(n, "custodyHeadHash")) + "\nVerify: " + J.s(n, "verifyUrl"));
        t.setEditable(false); t.setWrapText(true); t.setPrefSize(640, 420);
        Dialog<ButtonType> d = new Dialog<>(); d.setTitle(J.s(n, "certificateNo")); d.getDialogPane().setContent(t); d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); d.show();
    }

    public static void verifyDialog() {
        FormDialog.show("Verify a certificate", "Verify", List.of(FormDialog.text("no", "Certificate number", true), FormDialog.text("code", "Verification code (XXXX-XXXX-XXXX)", true)),
                b -> () -> ApiClient.get("/api/public/certificates/verify?no=" + java.net.URLEncoder.encode(b.path("no").asText(), java.nio.charset.StandardCharsets.UTF_8) + "&code=" + java.net.URLEncoder.encode(b.path("code").asText(), java.nio.charset.StandardCharsets.UTF_8)),
                r -> { StringBuilder sb = new StringBuilder(J.s(r, "message")); r.path("publicDetails").fields().forEachRemaining(e -> sb.append('\n').append(J.human(e.getKey())).append(": ").append(e.getValue().asText())); Alerts.info(sb.toString()); });
    }
}
