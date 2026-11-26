package com.ecycle.backend.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Device data-wiping checklists per product category (aligned with the NIST SP 800-88 Rev.1 Clear / Purge / Destroy vocabulary). */
public final class WipeChecklists {
    private WipeChecklists() {}
    public record Item(String code, String text, boolean mandatory) {}
    public static final List<String> METHODS = List.of("NIST 800-88 Clear", "NIST 800-88 Purge", "NIST 800-88 Destroy", "ATA Secure Erase", "NVMe Sanitize / Crypto Erase",
            "Factory reset with encryption key destruction", "Physical destruction (shred / degauss)");

    private static Item i(String c, String t, boolean m) { return new Item(c, t, m); }

    public static List<Item> forCategory(String categoryName) {
        String c = categoryName == null ? "" : categoryName.toLowerCase(Locale.ROOT);
        List<Item> l = new ArrayList<>();
        l.add(i("BACKUP_DONE", "Owner has backed up anything they want to keep (or accepts that all data will be lost)", true));
        l.add(i("ACCOUNTS_SIGNED_OUT", "Signed out of all accounts and removed the device from cloud accounts and licence managers", true));
        if (c.contains("phone") || c.contains("tablet")) {
            l.add(i("LOCK_REMOVED", "Activation / factory-reset protection lock removed (Find My, Google FRP, MDM enrolment)", true));
            l.add(i("SIM_SD_REMOVED", "SIM card and memory card removed", true));
            l.add(i("ENCRYPTION_ENABLED_BEFORE_RESET", "Device encryption was enabled before the factory reset so old data is cryptographically unrecoverable", false));
        } else if (c.contains("laptop") || c.contains("desktop")) {
            l.add(i("REMOVABLE_MEDIA_REMOVED", "Removable media removed (SD card, USB drives, optical discs, external disks)", true));
            l.add(i("BIOS_PASSWORD_CLEARED", "BIOS / UEFI passwords, TPM ownership and disk-encryption keys cleared", false));
        } else if (c.contains("printer")) {
            l.add(i("PRINTER_MEMORY_CLEARED", "Stored print/scan jobs, address book, Wi-Fi credentials and internal disk cleared", true));
        } else if (c.contains("circuit") || c.contains("component")) {
            l.add(i("MEMORY_CHIPS_IDENTIFIED", "Non-volatile memory chips (flash, EEPROM, SSD controllers) identified on the boards", true));
        } else {
            l.add(i("STORAGE_IDENTIFIED", "Any internal storage or memory in the device identified", true));
        }
        l.add(i("ERASE_PERFORMED", "Storage erased with a recognised method (select the method below)", true));
        l.add(i("ERASE_VERIFIED", "Erasure verified (tool report shows success and a sample read / boot test shows no user data)", true));
        l.add(i("SERIAL_RECORDED", "Device serial number / IMEI recorded in the erase report", true));
        l.add(i("REPORT_ATTACHED", "Erase report or log reference recorded (evidence reference below)", true));
        return l;
    }

    public static List<String> missingMandatory(String categoryName, Collection<String> completedCodes) {
        Set<String> done = new LinkedHashSet<>(completedCodes == null ? List.of() : completedCodes);
        return forCategory(categoryName).stream().filter(Item::mandatory).map(Item::code).filter(c -> !done.contains(c)).toList();
    }
    public static List<String> unknownCodes(String categoryName, Collection<String> codes) {
        Set<String> known = new LinkedHashSet<>(); forCategory(categoryName).forEach(x -> known.add(x.code()));
        return codes == null ? List.of() : codes.stream().filter(c -> !known.contains(c)).toList();
    }
    public static boolean knownMethod(String m) { return m != null && METHODS.contains(m); }
}
