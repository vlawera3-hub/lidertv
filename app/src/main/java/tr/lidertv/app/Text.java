package tr.lidertv.app;
import java.text.Normalizer;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
/** Android-independent normalization and identifiers; covered by core-tests. */
public final class Text {
    private Text() {}
    public static String fold(String s) {
        return Normalizer.normalize(s == null ? "" : s.toLowerCase(Locale.forLanguageTag("tr")), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").replace('\u0131','i');
    }
    public static String id(String s) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for(byte b:bytes) out.append(String.format(Locale.ROOT,"%02x",b & 255));
            return out.toString();
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
    public static String fts(String input, boolean cast) {
        StringBuilder b = new StringBuilder();
        String[] words = fold(input).replaceAll("[^\\p{L}\\p{N} ]", " ").trim().split("\\s+");
        for (int i=0;i<Math.min(words.length,8);i++) {
            String w=words[i]; if(w.isEmpty()) continue;
            if(b.length()>0) b.append(" AND ");
            if(cast) b.append("(title_key:").append(w).append("* OR cast_key:").append(w).append("*)");
            else b.append("title_key:").append(w).append('*');
        }
        return b.toString();
    }
    public static String safeError(Throwable e) {
        String n = e.getClass().getSimpleName();
        if(e instanceof java.net.UnknownHostException) return "Sunucu bulunamad\u0131. Adresi ve internet ba\u011flant\u0131s\u0131n\u0131 kontrol edin.";
        if(e instanceof java.net.SocketTimeoutException) return "Sunucu zaman\u0131nda yan\u0131t vermedi.";
        if(e instanceof javax.net.ssl.SSLException) return "Sunucunun g\u00fcvenlik sertifikas\u0131 do\u011frulanamad\u0131. Sertifika kontrol\u00fc atlanmad\u0131.";
        if(e instanceof UserError) return e.getMessage();
        return "\u0130\u015flem tamamlanamad\u0131 (" + n + "). Ba\u011flant\u0131 ve kaynak bi\u00e7imini kontrol edin.";
    }
    public static final class UserError extends java.io.IOException { public UserError(String s) { super(s); } }
}
