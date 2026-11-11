package tr.lidertv.app;
import java.io.*;import java.util.*;
public class CoreTest {
    static int n=0; static void check(boolean b,String name){if(!b)throw new AssertionError(name);n++;System.out.println("PASS "+name);}
    public static void main(String[] args)throws Exception{
        check(Text.fold("\u0130STANBUL I\u015eIK \u00c7\u0130\u011e").equals("istanbul isik cig"),"Turkish normalization");
        check(!M3uParser.validUrl("javascript:alert(1)"),"Unsafe URI rejected");
        check(M3uParser.validUrl("https://example.org/live/a.ts"),"HTTPS accepted");
        check(M3uParser.canonical("HTTPS://EXAMPLE.ORG/A?token=AbC#f").equals("https://example.org/A?token=AbC"),"Canonical URL preserves case-sensitive token");
        List<M3uParser.Entry> rows=new ArrayList<>();
        String s="\ufeff#EXTM3U\n#EXTINF:-1 group-title=\"Film, Sinema\" type=\"vod\" cast=\"Ay\u015fe Y\u0131lmaz\" added=\"1700000000\",Bir, Film\n#EXTVLCOPT:http-user-agent=LiderTv\nhttps://example.org/movie/1.mp4\n#EXTINF:-1 group-title=\"Dizi\",Dizi S01E01\nhttps://example.org/series/2.mkv\n#EXTINF:-1 type=\"live\",Haber\nhttps://example.org/live/a.ts\n";
        M3uParser.Stats st=M3uParser.parse(new StringReader(s),rows::add);
        check(st.records==3,"Streaming import record count");check(rows.get(0).title.equals("Bir, Film"),"Commas inside header attributes");
        check(rows.get(0).group.equals("Film, Sinema"),"Group with comma");check(rows.get(0).kind.equals("movies"),"Explicit VOD classification");
        check(rows.get(1).kind.equals("series"),"Series path classification");check(st.inferred==1,"Inferred type counter");
        check(rows.get(0).added==1700000000000L,"Source date in seconds");check(rows.get(0).userAgent.equals("LiderTv"),"Per-stream User-Agent");
        check(Text.fts("ali OR - * \"",true).equals("(title_key:ali* OR cast_key:ali*) AND (title_key:or* OR cast_key:or*)"),"FTS expressions escaped");
        boolean failed=false;try{M3uParser.parse(new StringReader("<html>error</html>"),e->{});}catch(Text.UserError e){failed=true;}check(failed,"Invalid list refused");
        Set<String> ids=new HashSet<>();for(String u:Arrays.asList("https://x.test/a","https://x.test/a","https://x.test/a?token=other"))ids.add(Text.id("source\u0000live\u0000"+u));
        check(ids.size()==2,"Duplicates removed without dropping token variants");
        boolean hls=false;try{M3uParser.parse(new StringReader("#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:10,\nhttps://example.org/segment.ts\n"),e->{});}catch(Text.UserError e){hls=true;}check(hls,"HLS segment manifest not imported as catalog");
        List<M3uParser.Entry> explicit=new ArrayList<>();M3uParser.parse(new StringReader("#EXTM3U\n#EXTINF:-1 type=\"movies\",Film\nhttps://example.org/file\n"),explicit::add);check(explicit.get(0).kind.equals("movies")&&!explicit.get(0).inferred,"Explicit plural movies type accepted");
        System.out.println(n+" core checks passed.");
    }
}
