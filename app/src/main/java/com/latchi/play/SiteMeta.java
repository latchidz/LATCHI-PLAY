package com.latchi.play;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared helpers to read public metadata from a site's own HTML/meta tags.
 * Only reads fields the site publishes; never invents data.
 */
public final class SiteMeta {
    private static final Pattern OG_PROPERTY = Pattern.compile(
            "<meta[^>]+property=\"([^\"]+)\"[^>]+content=\"([^\"]*)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern META_NAME = Pattern.compile(
            "<meta[^>]+name=\"([^\"]+)\"[^>]+content=\"([^\"]*)\"",
            Pattern.CASE_INSENSITIVE);

    private static final Map<String, String> GENRE_MAP = new LinkedHashMap<>();

    static {
        GENRE_MAP.put("اكشن", "أكشن");
        GENRE_MAP.put("أكشن", "أكشن");
        GENRE_MAP.put("جريمة", "جريمة");
        GENRE_MAP.put("دراما", "دراما");
        GENRE_MAP.put("كوميدي", "كوميدي");
        GENRE_MAP.put("كوميديا", "كوميديا");
        GENRE_MAP.put("رعب", "رعب");
        GENRE_MAP.put("اثارة", "إثارة");
        GENRE_MAP.put("إثارة", "إثارة");
        GENRE_MAP.put("خيال علمي", "خيال علمي");
        GENRE_MAP.put("فانتازيا", "فانتازيا");
        GENRE_MAP.put("مغامرة", "مغامرة");
        GENRE_MAP.put("رومانسي", "رومانسي");
        GENRE_MAP.put("رومانسية", "رومانسية");
        GENRE_MAP.put("انيميشن", "أنيميشن");
        GENRE_MAP.put("أنيميشن", "أنيميشن");
        GENRE_MAP.put("وثائقي", "وثائقي");
        GENRE_MAP.put("رياضة", "رياضة");
        GENRE_MAP.put("غموض", "غموض");
        GENRE_MAP.put("تاريحي", "تاريخي");
        GENRE_MAP.put("حربي", "حربي");
        GENRE_MAP.put("عائلي", "عائلي");
        GENRE_MAP.put("موسيقي", "موسيقي");
        GENRE_MAP.put("غربية", "غربية");
    }

    private SiteMeta() {
    }

    public static String og(String html, String property) {
        Matcher matcher = OG_PROPERTY.matcher(html == null ? "" : html);
        while (matcher.find()) {
            if (property.equalsIgnoreCase(matcher.group(1))) {
                return matcher.group(2).trim();
            }
        }
        return "";
    }

    public static String meta(String html, String name) {
        Matcher matcher = META_NAME.matcher(html == null ? "" : html);
        while (matcher.find()) {
            if (name.equalsIgnoreCase(matcher.group(1))) {
                return matcher.group(2).trim();
            }
        }
        return "";
    }

    /** Extracts "8" from ratingValue and "200" from ratingCount patterns. */
    public static float rating(String html) {
        Matcher matcher = Pattern.compile("ratingValue\">([\\d.]+)<").matcher(html == null ? "" : html);
        if (matcher.find()) {
            try {
                return Float.parseFloat(matcher.group(1));
            } catch (Exception ignored) {
                // not a number
            }
        }
        return 0f;
    }

    public static int ratingCount(String html) {
        Matcher matcher = Pattern.compile("ratingCount\">([\\d]+)<").matcher(html == null ? "" : html);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (Exception ignored) {
                // not a number
            }
        }
        return 0;
    }

    /** First 4-digit year (1900-2099) found in the text. */
    public static String year(String text) {
        if (text == null) return "";
        Matcher matcher = Pattern.compile("\\b(19|20)\\d{2}\\b").matcher(text);
        if (matcher.find()) return matcher.group(0);
        return "";
    }

    /** Maps known Arabic genre keywords found in the text (deduplicated, order-preserving). */
    public static String genres(String text) {
        if (text == null || text.isEmpty()) return "";
        String lower = text.toLowerCase(Locale.US);
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, String> entry : GENRE_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) {
                if (!found.contains(entry.getValue())) found.add(entry.getValue());
                if (found.size() >= 6) break;
            }
        }
        return String.join(" • ", found);
    }

    /** Cast names from "بطولة ..." / "للنجم ..." / "النجوم ..." patterns in public text. */
    public static List<String> cast(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;
        Matcher matcher = Pattern.compile("(?:بطولة|للنجم|النجوم)\\s*:?\\s*([^<]{3,140})")
                .matcher(text);
        if (matcher.find()) {
            String segment = matcher.group(1).trim();
            // Cut at quotes / ellipsis and at filler words so only names remain.
            for (String marker : new String[]{"\"", "...", "&gt;", "&quot;"}) {
                int index = segment.indexOf(marker);
                if (index > 0) {
                    segment = segment.substring(0, index);
                    break;
                }
            }
            String[] fillers = {"كامل", "بجودة", "مشاهدة", "تحميل", "اون لاين", "اونلاين",
                    "مترجم", "حصريا", "وتحميل", "HD", "720p", "1080p", "4K", "Online", "WEB-DL"};
            for (String filler : fillers) {
                int index = segment.indexOf(filler);
                if (index > 0) {
                    segment = segment.substring(0, index);
                    break;
                }
            }
            for (String part : segment.split("\\s+و\\s+")) {
                String name = part.trim().replaceAll("^\\W+|\\W+$", "");
                if (name.length() >= 3 && name.length() <= 40 &&
                        !name.contains("...") && !result.contains(name)) {
                    result.add(name);
                    if (result.size() >= 6) break;
                }
            }
        }
        return result;
    }

    /** Strips HTML tags from a fragment. */
    public static String text(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }
}
