import javax.tools.*;
import com.sun.source.util.JavacTask;
import java.nio.file.*;
import java.util.*;
/** Syntax-only validation without Android SDK. This is NOT an Android compilation. */
class ParseJava {
 public static void main(String[] args)throws Exception {
  JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();DiagnosticCollector<JavaFileObject> errors=new DiagnosticCollector<>();
  try(StandardJavaFileManager fm=compiler.getStandardFileManager(errors,null,null)){
   List<java.io.File> files=new ArrayList<>();try(var paths=Files.walk(Path.of(args[0]))){paths.filter(p->p.toString().endsWith(".java")).forEach(p->files.add(p.toFile()));}
   JavacTask task=(JavacTask)compiler.getTask(null,fm,errors,List.of("-proc:none","-encoding","UTF-8"),null,fm.getJavaFileObjectsFromFiles(files));
   for(var tree:task.parse()) System.out.println("PARSED "+tree.getSourceFile().getName());
   for(var d:errors.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR){System.err.println(d);System.exit(1);}
   System.out.println(files.size()+" Java files parsed; no Android type checking or APK build performed.");
  }
 }
}
