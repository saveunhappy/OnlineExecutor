package org.olexec.compile;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringSourceCompiler {
    private static Map<String, JavaFileObject> fileObjectMap = new ConcurrentHashMap<>();

    /** 使用 Pattern 预编译功能 */
    private static Pattern CLASS_PATTERN = Pattern.compile("class\\s+([$_a-zA-Z][$_a-zA-Z0-9]*)\\s*");

    public static byte[] compile(String source, DiagnosticCollector<JavaFileObject> compileCollector) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        JavaFileManager javaFileManager = new TmpJavaFileManager(compiler.getStandardFileManager(compileCollector, null, null));

        // 从源码字符串中匹配类名
        Matcher matcher = CLASS_PATTERN.matcher(source);
        String className;
        if (matcher.find()) {
            className = matcher.group(1);
        } else {
            throw new IllegalArgumentException("No valid class");
        }

        // 把源码字符串构造成JavaFileObject，供编译使用，className传到构造器中会生成一个uri，
        // uri是String:///Run.java，代表一个资源，然后是源代码
        JavaFileObject sourceJavaFileObject = new TmpJavaFileObject(className, source);
       //out：编译器的一个额外的输出 Writer，为 null 的话就是 System.err；
        // options：编译器的配置；
        // classes：需要被 annotation processing 处理的类的类名；
        // compilationUnits：要被编译的单元们，就是一堆 JavaFileObject。
        // 一个class就是一个编译单元，一个Java文件中写了好多个class,
        // 在jvm看来他们是多个，也就是多个编译单元
        Boolean result = compiler.getTask(null, javaFileManager, compileCollector,
                null, null, Arrays.asList(sourceJavaFileObject)).call();
        //这个时候返回的就是字节码文件了，因为这个是接口，下面强转之后就是字节数组了。
        JavaFileObject bytesJavaFileObject = fileObjectMap.get(className);
        if (result && bytesJavaFileObject != null) {
            return ((TmpJavaFileObject) bytesJavaFileObject).getCompiledBytes();
        }
        return null;
    }

    /**
     * 管理JavaFileObject对象的工具
     */
    public static class TmpJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {
        protected TmpJavaFileManager(JavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public JavaFileObject getJavaFileForInput(JavaFileManager.Location location, String className, JavaFileObject.Kind kind) throws IOException {
            JavaFileObject javaFileObject = fileObjectMap.get(className);
            if (javaFileObject == null) {
                return super.getJavaFileForInput(location, className, kind);
            }
            return javaFileObject;
        }

        @Override
        public JavaFileObject getJavaFileForOutput(JavaFileManager.Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
            //在调用了compiler.getTask().call()之后，
            // 就会到这里，注意第二个参数，不是String了，
            // 是kind类型的一个东西，这里就是说你最终要生成一个.class文件
            JavaFileObject javaFileObject = new TmpJavaFileObject(className, kind);
            fileObjectMap.put(className, javaFileObject);
            return javaFileObject;
        }
    }

    /**
     * 用来封装表示源码与字节码的对象
     */
    public static class TmpJavaFileObject extends SimpleJavaFileObject {
        private String source;
        private ByteArrayOutputStream outputStream;

        /**
         * 构造用来存储源代码的JavaFileObject
         * 需要传入源码source，然后调用父类的构造方法创建kind = Kind.SOURCE的JavaFileObject对象
         */
        public TmpJavaFileObject(String name, String source) {
            super(URI.create("String:///" + name + Kind.SOURCE.extension), Kind.SOURCE);
            this.source = source;
        }

        /**
         * 构造用来存储字节码的JavaFileObject
         * 需要传入kind，即我们想要构建一个存储什么类型文件的JavaFileObject
         */
        public TmpJavaFileObject(String name, Kind kind) {
            super(URI.create("String:///" + name + Kind.SOURCE.extension), kind);
            this.source = null;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            //这个别的地方会调用，就像antlr一样，获取到传入的字节码对象
            if (source == null) {
                throw new IllegalArgumentException("source == null");
            }
            return source;
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            outputStream = new ByteArrayOutputStream();
            //别的地方会调用，你这里创建好了之后，返回回去，别的位置得到这个流之后就会往里面写东西
            return outputStream;
        }

        public byte[] getCompiledBytes() {
            //获取字节数组，别的地方拿到openOutputStream之后，就写入了，这个时候里面有内容了，就可以写入了。
            return outputStream.toByteArray();
        }
    }
}
