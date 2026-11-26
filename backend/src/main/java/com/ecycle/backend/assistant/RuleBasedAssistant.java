package com.ecycle.backend.assistant;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.exception.ApiException;
import java.util.*;
import java.util.regex.*;

/** Deterministic fallback used when no LLM is configured or reachable. Understands a small set of intents and a few general e-waste questions; uses the same actions (and confirmations) as the LLM tools. */
public class RuleBasedAssistant {
    private static final Pattern CODE = Pattern.compile("\\bPK-[A-Z0-9]{7}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BOOK = Pattern.compile("book.*pickup.*?(?:recycler|facility|#)\\s*#?(\\d+).*?[\"“']([^\"”']{3,200})[\"”'].*?(\\d+(?:\\.\\d+)?)\\s*kg.*?(\\d{4}-\\d{2}-\\d{2}(?:T\\d{2}:\\d{2})?|tomorrow)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern REDEEM = Pattern.compile("redeem.*?(?:reward\\s*)?#?(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WEIGHT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*kg", Pattern.CASE_INSENSITIVE);
    private static final Pattern CITY = Pattern.compile("\\bin\\s+([A-Za-z][A-Za-z .'-]{1,40})", Pattern.CASE_INSENSITIVE);

    public record Result(String reply, List<String> tools) {}
    private final AssistantActions actions; private final AuthUser me; private final EcoTools tools;
    public RuleBasedAssistant(AssistantActions actions, AuthUser me, EcoTools tools) { this.actions = actions; this.me = me; this.tools = tools; }

    public String answer(String raw) {
        String q = raw.trim(), l = q.toLowerCase(Locale.ROOT);
        Matcher m;
        if ((m = BOOK.matcher(q)).find()) return tools.proposeBookPickup(Long.parseLong(m.group(1)), m.group(2), Double.parseDouble(m.group(3)), 1, m.group(4), l.contains("laptop") || l.contains("phone") || l.contains("drive") || l.contains("tablet"), null);
        if (l.contains("cancel") && (m = CODE.matcher(q)).find()) return tools.proposeCancelPickup(m.group().toUpperCase(Locale.ROOT));
        if (l.contains("redeem") && (m = REDEEM.matcher(q)).find()) return tools.proposeRedeemReward(Long.parseLong(m.group(1)));
        if ((m = CODE.matcher(q)).find()) return tools.getPickupStatus(m.group().toUpperCase(Locale.ROOT));
        if (l.contains("book") && l.contains("pickup")) return "To book a pickup, tell me: the facility number (ask \"find recyclers\" to see them), what you are recycling, its weight and a date. For example: book a pickup at recycler #1 for \"old laptop\" 2.5 kg on 2026-12-01.";
        if (l.matches(".*\\b(points?|balance|level|badges?|rank)\\b.*")) return tools.getMyEcoPoints();
        if (l.contains("pickup") || l.contains("collection")) return tools.listMyPickups();
        if (l.contains("reward") || l.contains("redeem") || l.contains("voucher")) return tools.listRewards() + "\nTo redeem, say \"redeem reward <number>\".";
        if (l.contains("certificate")) return tools.listMyCertificates();
        if (l.contains("recycler") || l.contains("facility") || l.contains("drop off") || l.contains("drop-off") || l.contains("nearest")) {
            Matcher c = CITY.matcher(q); return tools.findRecyclers(c.find() ? c.group(1).trim() : null, null);
        }
        if ((l.contains("carbon") || l.contains("impact") || l.contains("co2") || l.contains("estimate")) && (m = WEIGHT.matcher(q)).find()) return tools.estimateImpact(Double.parseDouble(m.group(1)), 1, "RECYCLE", null);
        return knowledge(l);
    }

    static String knowledge(String l) {
        if (l.contains("battery") || l.contains("batteries")) return "Never put lithium batteries in household bins - they can start fires in trucks and plants. Tape the terminals, keep them separate and hand them to a recycler or a battery drop-off. Swollen or damaged batteries should be flagged as hazardous when you list or book.";
        if (l.contains("wipe") || l.contains("data") || l.contains("sanitiz") || l.contains("erase")) return "Before any device leaves you: back up, sign out of accounts, remove the SIM and memory cards, then erase the storage (factory reset plus encryption for phones; secure erase or crypto erase for laptops and drives, e.g. NIST 800-88 Clear/Purge). E-CYCLE gives you a checklist per device type, and an administrator or the recycler verifies it before a certificate is issued.";
        if (l.contains("certificate")) return "E-CYCLE issues PDF certificates (data sanitization, data destruction, recycling) after verification. Each has a number, a verification code and a QR code that opens the public verification page, and the signed record is tied to the item's chain of custody.";
        if (l.contains("custody") || l.contains("qr")) return "Every item has a QR passport. Each scan or handover is added to a timestamped, per-asset audit log where every entry contains the SHA-256 hash of the previous one, so later edits are detectable.";
        if (l.contains("recycle") || l.contains("e-waste") || l.contains("ewaste")) return "Prefer reuse or repair first, then refurbishment, then recycling; recycling recovers metals but loses the embodied energy of the device. Use only certified recyclers and keep the certificate.";
        return "I can check your eco-points, list or track pickups, find recyclers, show rewards and certificates, estimate impact (e.g. \"impact of 5 kg\"), and prepare a pickup booking, cancellation or reward redemption for you to confirm. I can also answer general e-waste questions. (No AI model is configured on this server, so I understand a limited set of phrasings.)";
    }
}
