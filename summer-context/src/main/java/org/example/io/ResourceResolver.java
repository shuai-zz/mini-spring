package org.example.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 负责扫描并列出所有文件，由客户端决定是找出.class文件，还是找出.properties文件
 */
public class ResourceResolver {
    Logger logger = LoggerFactory.getLogger(getClass());
    String basePackage;

    public ResourceResolver(String basePackage){
        this.basePackage = basePackage;
    }


    /**
     * 获取扫描到的Resource
     * @param mapper Resource到Class Name的映射，就可以扫描出Class Name
     * @return 映射后的资源列表
     * @param <R> return type
     */
    public <R> List<R> scan(Function<Resource, R> mapper){
        // org.example.service -> org/example/service
        String basePackagePath=this.basePackage.replace(".", "/");
        String path;
        path = basePackagePath;
        try {
            // 结果收集器collector
            List<R> collector = new ArrayList<>();
            scan0(basePackagePath, path, collector, mapper);
            return collector;
        }catch(IOException e){
            throw new UncheckedIOException(e);
        }catch(URISyntaxException e){
            throw new RuntimeException(e);
        }
    }


    <R> void scan0(String basePackagePath, String path, List<R> collector, Function<Resource, R> mapper) throws IOException, URISyntaxException {
        // 打印调试日志，记录当前扫描的路径
        logger.atDebug().log("scan path: {}", path);
        // 获取类加载器，并查找指定path下的所有资源URL
        Enumeration<URL> en = getContextClassLoader().getResources(path);

        while (en.hasMoreElements()) {
            URL url = en.nextElement();
            URI uri = url.toURI(); // URL -> URI
            // 规范URI格式（去除末尾斜杆）
            String uriStr = removeTrailingSlash(uriToString(uri));
            // 基础存储路径
            String uriBaseStr = uriStr.substring(0, uriStr.length() - basePackagePath.length());

            // 处理本地文件协议，去除file: 前缀
            if (uriBaseStr.startsWith("file:")) {
                uriBaseStr = uriBaseStr.substring(5);
            }
            // 区分jar和本地文件资源
            if (uriStr.startsWith("jar:")) {
                scanFile(true, uriBaseStr, jarUriToPath(basePackagePath, uri), collector, mapper);
            } else {
                scanFile(false, uriBaseStr, Paths.get(uri), collector, mapper);
            }
        }
    }


    /**
     * 获取当前线程的ClassLoader，如果为空则使用当前类的ClassLoader
     * @return ClassLoader
     */
    ClassLoader getContextClassLoader() {
        ClassLoader cl = null;
        cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = getClass().getClassLoader();
        }
        return cl;
    }

    Path jarUriToPath(String basePackagePath, URI jarUri) throws IOException {
        return FileSystems.newFileSystem(jarUri, Map.of()).getPath(basePackagePath);
    }


    <R> void scanFile(boolean isJar, String base, Path root, List<R> collector, Function<Resource, R> mapper) throws IOException {
        String baseDir = removeTrailingSlash(base);
        // 遍历root路径下的所有常规文件（Files.walk: 递归遍历子目录）
        Files.walk(root).filter(Files::isRegularFile).forEach(file -> {
            Resource res = null;
            if (isJar) {
                // 处理JAR内资源：baseDir是path，removeLeadingSlash(file.toString())是name
                res = new Resource(baseDir, removeLeadingSlash(file.toString()));
            } else {
                // 处理本地文件资源
                String path = file.toString();
                String name = removeLeadingSlash(path.substring(baseDir.length()));
                res = new Resource("file:" + path, name);
            }
            logger.atDebug().log("found resource: {}", res);
            // 调用外部传入的转换逻辑，将Resource转为目标类型R
            R r = mapper.apply(res);
            // 若不为空，添加到collector中
            if (r != null) {
                collector.add(r);
            }
        });
    }

    /**
     * URI转String
     * @param uri URI
     * @return String
     */
    String uriToString(URI uri) {
        return URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8);
    }

    /**
     * 去除字符串开头的斜杠
     * @param s String
     * @return String
     */
    String removeLeadingSlash(String s) {
        if (s.startsWith("/") || s.startsWith("\\")) {
            s = s.substring(1);
        }
        return s;
    }

    /**
     * 去除字符串的末尾斜杠
     * @param s String
     * @return  String
     */
    String removeTrailingSlash(String s) {
        if (s.endsWith("/") || s.endsWith("\\")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

}
